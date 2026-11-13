# PayLink

- `transfer-service`: Question 1, the P2P transfer API (Spring Boot, H2, `JdbcClient`).
- `bank-search`: Question 2, the bank name type-ahead (plain Java).

You only need Java 21. Make sure `JAVA_HOME` points to the JDK folder, not its `bin` folder. The wrapper downloads Maven itself.

```sh
./mvnw verify
```

On Windows, use `mvnw.cmd`.

## Question 1

```sh
./mvnw -pl transfer-service spring-boot:run
```

It runs on port 8080 and keeps its data in `transfer-service/data/`. Delete that folder to reset. These wallets are seeded:

| Wallet | Currency | Status | Balance | Created |
| --- | --- | --- | --- | --- |
| W1001 | NGN | ACTIVE | 1,000,000.00 | 90 days ago |
| W2002 | NGN | ACTIVE | 500,000.00 | 90 days ago |
| W3003 | NGN | ACTIVE | 2,000,000.00 | 2 days ago |
| W4004 | NGN | SUSPENDED | 10,000.00 | 90 days ago |
| W5005 | USD | ACTIVE | 1,000.00 | 90 days ago |

The same requests are in `transfer-service/requests.http` if you use the REST Client extension or IntelliJ.

### Examples

To keep the commands short:

```sh
URL=http://localhost:8080/api/v1/transfers
JSON='Content-Type: application/json'
```

**201**: a successful transfer. Send it again and you get the same response back, without a second debit.

```sh
curl -i -X POST $URL -H "$JSON" -H 'Idempotency-Key: c79b70d1-77bc-4f1b-a36f-b921dcfe7a15' \
  -d '{"sourceWalletId":"W1001","destinationWalletId":"W2002","amount":"25000.00","currency":"NGN","remarks":"Rent share"}'
```

**200**: look a transfer up by its RRN.

```sh
curl -i $URL/<rrn>
```

**422 IDEMPOTENCY_KEY_REUSED**: the same key with a different amount.

```sh
curl -i -X POST $URL -H "$JSON" -H 'Idempotency-Key: c79b70d1-77bc-4f1b-a36f-b921dcfe7a15' \
  -d '{"sourceWalletId":"W1001","destinationWalletId":"W2002","amount":"30000.00","currency":"NGN","remarks":"Rent share"}'
```

**400 INVALID_REQUEST**: no key. A bad key, a bad amount, the suspended W4004 or the USD wallet W5005 give the same error.

```sh
curl -i -X POST $URL -H "$JSON" \
  -d '{"sourceWalletId":"W1001","destinationWalletId":"W2002","amount":"100.00","currency":"NGN"}'
```

**404 WALLET_NOT_FOUND**

```sh
curl -i -X POST $URL -H "$JSON" -H 'Idempotency-Key: d381cc5d-71db-463b-a519-ecd6578e7ba9' \
  -d '{"sourceWalletId":"W1001","destinationWalletId":"W9999","amount":"100.00","currency":"NGN"}'
```

**404 TRANSFER_NOT_FOUND**

```sh
curl -i $URL/000000000000
```

**409 REQUEST_IN_PROGRESS**: two copies of the same request sent together. One succeeds and the other gets 409.

```sh
for i in 1 2; do
  curl -s -X POST $URL -H "$JSON" -H 'Idempotency-Key: 21f32aa0-21bb-4da1-b8b3-8a3ecd24f71e' \
    -d '{"sourceWalletId":"W2002","destinationWalletId":"W1001","amount":"100.00","currency":"NGN"}' &
done; wait
```

**422 INSUFFICIENT_FUNDS**

```sh
curl -i -X POST $URL -H "$JSON" -H 'Idempotency-Key: 97298dc1-100c-4ef1-9e9d-c4a2309557ff' \
  -d '{"sourceWalletId":"W2002","destinationWalletId":"W1001","amount":"900000.00","currency":"NGN"}'
```

**422 RISK_DECLINED**: W3003 is only two days old and the amount is over 500,000.

```sh
curl -i -X POST $URL -H "$JSON" -H 'Idempotency-Key: 233066f0-82d5-40a1-be48-dff39146bb15' \
  -d '{"sourceWalletId":"W3003","destinationWalletId":"W1001","amount":"600000.00","currency":"NGN"}'
```

**429 RATE_LIMITED**: the sixth new transfer from one wallet within 60 seconds. Check the `Retry-After` header.

```sh
for key in 86a7c391-c318-4e68-8d6a-34e715af2b77 72ac3c72-4351-4f3f-8131-2d3e32eea569 \
           de698b17-bd34-436e-a1f8-dc2acbecf0ab 8ed60e6f-1bd3-439a-91ed-846d2c7ac1e7 \
           a93b51db-de2c-4f87-91cc-2408432f4170 139a408a-9852-46a6-b79b-b3d8937eb5f1; do
  curl -s -i -X POST $URL -H "$JSON" -H "Idempotency-Key: $key" \
    -d '{"sourceWalletId":"W3003","destinationWalletId":"W2002","amount":"1.00","currency":"NGN"}' | head -1
done
```

**503 RISK_UNAVAILABLE**: the fake risk service fails 20% of the time, so all three tries fail about once in 125 transfers. Keep sending this with a new key until you see it.

```sh
curl -i -X POST $URL -H "$JSON" -H 'Idempotency-Key: b44910cd-8f07-446b-9eae-e9eccaf9b459' \
  -d '{"sourceWalletId":"W1001","destinationWalletId":"W2002","amount":"1.00","currency":"NGN"}'
```

**500 INTERNAL_ERROR** can't be triggered by a normal request. Unexpected errors are logged and returned with this code, without a stack trace.

### Two instances on one database

With the first instance still running, start a second one on port 8081:

```sh
./mvnw -pl transfer-service spring-boot:run -Dspring-boot.run.arguments=--server.port=8081
```

`AUTO_SERVER=TRUE` lets both open the same H2 file, and both take RRN numbers from the same sequence. Send transfers to both ports and the RRNs never clash.

### H2 console

Go to http://localhost:8080/h2-console. Use JDBC URL `jdbc:h2:file:./data/paylink;AUTO_SERVER=TRUE`, user `sa` and no password.

## Question 2

```sh
./mvnw -pl bank-search test
```

The latency test prints the p99 it measured.
