# Design

## Components

- **Controller layer**: `TransferController` and `ApiExceptionHandler`.
- **TransferService**: the transfer flow.
- **Repositories**: wallets, transfers, ledger and idempotency keys, all plain SQL on `JdbcClient`.
- **RrnGenerator**: builds the 12-digit RRN.
- **RetryExecutor**: retries the risk call.

A transfer runs in three short transactions:

- **Tx A** claims the idempotency key, locks the source wallet, checks the wallets and the rate limit, and inserts the transfer as INITIATED.
- **Risk screening** comes next, outside any transaction.
- **Tx B** marks the transfer RISK_APPROVED.
- **Tx C** locks both wallets, moves the money, writes the DEBIT and CREDIT entries, marks the transfer COMPLETED and saves the response against the key.

If risk declines or is down, or the money isn't there, the transfer goes to FAILED and the response is saved in the same transaction.

## Preventing double debits

The idempotency key is a primary key. Only one request can insert it, and any later request with that key gets the saved response, a 409 or a 422.

The response is saved in the same transaction that moves the money. You can't end up with money moved and no saved response, or the other way round.

Overdraft is blocked three times over:

- the `FOR UPDATE` lock on the wallet row
- the `UPDATE ... WHERE balance >= :amount` that must change exactly one row
- the `CHECK (balance >= 0)` constraint

Status changes are compare-and-set, so a transfer can't be completed twice.

## No deadlocks

Tx C always locks the lower wallet id first. That means W1001→W2002 and W2002→W1001 queue on the same lock instead of each holding one. Tx A only ever holds one wallet lock, so it can't be part of a cycle. Nothing holds a lock while waiting on the risk service.

## RRNs don't repeat

The last six digits come from a database sequence. That survives restarts and is shared by every instance using the same database. A repeat needs a million transfers in one hour, or the one-digit year to come round after ten years. Even then the primary key rejects the insert, and the service tries again with the next number.

## Known limits

- If the app dies right after Tx A, the key stays IN_PROGRESS and returns 409 until its 24-hour TTL runs out.
- If the app dies between Tx B and Tx C, the transfer stays RISK_APPROVED with no money moved. A recovery job would be needed to settle it.
- This uses H2, not Postgres. The RRN retry relies on H2 keeping the transaction open after a failed insert. Postgres would need a savepoint.
- Expired keys are only cleaned up when someone reuses them, since there is no scheduler.
