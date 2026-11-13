# Assumptions

## Question 1

- Assumed RRN time fields are UTC because the sample RRN only lines up with its `createdAt` in UTC.
- Assumed an RRN is only issued after validation and the wallet checks pass because a rejected request isn't a transfer.
- Assumed inactive wallets and currency mismatches are 400 because the request can't succeed as written; 404 is only for wallets that don't exist.
- Assumed `25000` and `25000.00` are the same request and different remarks are not because that's how a person would read them.
- Assumed 400, 404 and 429 aren't saved against the key because no transfer exists yet, so the client can fix the request and retry with the same key.
- Assumed 201, 422 and 503 are saved against the key because a transfer exists and a replay should report what happened to it.
- Assumed every validated new request counts toward the rate limit, even if it fails later, because it still cost us a risk call and database work.
- Assumed a 4xx from the risk service fails as RISK_UNAVAILABLE because retrying won't help and money shouldn't move without screening.
- Assumed key reuse (422) wins over in progress (409) because a different body is a client mistake whatever the first request is doing.
- Assumed RRNs only need to be unique for 10 years because the year is a single digit; the primary key and one retry cover a sequence wrap.
- Assumed the risk stub isn't one of the five components because the brief supplies it.
- Assumed an unknown RRN returns 404 TRANSFER_NOT_FOUND because the brief doesn't define that case.
- Assumed 409 and key-reuse 422 include the original RRN when there is one because `rrn` should appear whenever a transfer exists.
- Assumed expired keys are deleted when reused because there's no scheduler.

## Question 2

- Assumed dots and apostrophes are removed while other punctuation splits words because "G.T.B" should match "GTB" and "Stanbic-IBTC" should still match "ibtc".
- Assumed name ties ignore case because users don't care about case when reading a list.
- Assumed a selection made during a reload can be lost because `load` brings the real popularity numbers.
- Assumed bank codes are unique because `recordSelection` only gets a code.
