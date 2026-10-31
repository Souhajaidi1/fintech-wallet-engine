# API examples

A complete walkthrough with `curl`. Assumes the stack is running on `http://localhost:8080`.

## 1. Register

```bash
curl -sX POST http://localhost:8080/api/v1/auth/register \
  -H 'Content-Type: application/json' \
  -d '{"email":"souha@example.test","password":"a-long-enough-passphrase","fullName":"Souha Jaidi"}'
```

```json
{
  "id": "8c1f...",
  "email": "souha@example.test",
  "fullName": "Souha Jaidi",
  "role": "USER",
  "createdAt": "2026-09-08T09:00:00Z"
}
```

## 2. Log in

```bash
TOKEN=$(curl -sX POST http://localhost:8080/api/v1/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"email":"souha@example.test","password":"a-long-enough-passphrase"}' \
  | jq -r .accessToken)
```

## 3. Open a wallet

```bash
curl -sX POST http://localhost:8080/api/v1/wallets \
  -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' \
  -d '{"label":"Everyday","currency":"USD"}'
```

```json
{
  "id": "3ab9...",
  "label": "Everyday",
  "currency": "USD",
  "balance": "0.00",
  "balanceMinor": 0,
  "status": "ACTIVE",
  "createdAt": "2026-09-08T09:01:00Z"
}
```

Balances come back as a decimal **string** plus an integer minor-unit field. A JSON number would be parsed as a double by most clients, which is exactly the loss of precision the ledger exists to prevent.

## 4. Deposit, idempotently

```bash
curl -sX POST http://localhost:8080/api/v1/wallets/3ab9.../deposit \
  -H "Authorization: Bearer $TOKEN" \
  -H 'Idempotency-Key: 4d2c8a1e-7b3f-4a90-9e21-5c6d8f0a1b23' \
  -H 'Content-Type: application/json' \
  -d '{"amount":"250.00","reference":"opening balance"}'
```

`201 Created`, header `Idempotent-Replay: false`.

Send the identical request again:

`200 OK`, header `Idempotent-Replay: true`, the **same** transaction id, and the balance is still 250.00.

## 5. Transfer

```bash
curl -sX POST http://localhost:8080/api/v1/transfers \
  -H "Authorization: Bearer $TOKEN" \
  -H 'Idempotency-Key: 9f1b3d7c-2e58-4c11-8a6d-3b0f9c4e7a52' \
  -H 'Content-Type: application/json' \
  -d '{"sourceWalletId":"3ab9...","destinationWalletId":"7de2...","amount":"30.50","reference":"split dinner"}'
```

```json
{
  "id": "b41e...",
  "type": "TRANSFER",
  "status": "POSTED",
  "amount": "30.50",
  "amountMinor": 3050,
  "currency": "USD",
  "reference": "split dinner",
  "createdAt": "2026-09-08T09:05:00Z"
}
```

## 6. Error responses

Every error is an RFC 7807 problem document with a stable `code`.

**Insufficient funds — 422**

```json
{
  "type": "https://github.com/Souhajaidi1/fintech-wallet-engine/errors",
  "title": "Unprocessable Entity",
  "status": 422,
  "detail": "The wallet does not hold enough funds.",
  "instance": "/api/v1/transfers",
  "code": "INSUFFICIENT_FUNDS",
  "timestamp": "2026-09-08T09:06:00Z"
}
```

**Idempotency key reused with a different body — 422**

```json
{
  "status": 422,
  "detail": "This Idempotency-Key was already used for a different request body.",
  "code": "IDEMPOTENCY_KEY_REUSED"
}
```

**Validation failure — 400**, with the offending fields named:

```json
{
  "status": 400,
  "detail": "One or more fields are invalid.",
  "code": "VALIDATION_FAILED",
  "violations": {
    "amount": "amount may have at most 2 decimal places"
  }
}
```

**Someone else's wallet — 404**, not 403. A 403 would confirm the id exists.

## 7. History

```bash
curl -s "http://localhost:8080/api/v1/transactions?page=0&size=20" \
  -H "Authorization: Bearer $TOKEN"
```

```json
{
  "content": [ { "id": "b41e...", "type": "TRANSFER", "amount": "30.50" } ],
  "page": 0,
  "size": 20,
  "totalElements": 2,
  "totalPages": 1,
  "last": true
}
```

Page size is capped at 100, so no single request can ask the database for an entire history.

## 8. Reconciliation (admin)

```bash
curl -s http://localhost:8080/api/v1/admin/ledger/wallets/3ab9.../reconciliation \
  -H "Authorization: Bearer $ADMIN_TOKEN"
```

```json
{
  "walletId": "3ab9...",
  "cachedMinor": 21950,
  "ledgerMinor": 21950,
  "driftMinor": 0,
  "reconciled": true
}
```

The cached balance is recomputed from the ledger postings. `driftMinor` must always be zero.
