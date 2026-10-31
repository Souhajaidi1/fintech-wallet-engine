# ADR-005 — Double-entry ledger

**Status:** accepted · **Date:** 2026-09

## Context

The simplest wallet keeps a balance column and adjusts it:

```java
wallet.setBalance(wallet.getBalance().add(amount));
```

This is what most tutorials do, and it fails in ways that only show up later. There is no record of *why* a balance is what it is. A bug that adds to one wallet without subtracting from another creates money, and nothing detects it. Reconciliation is impossible because there is nothing to reconcile against.

## Decision

Every movement of money is a **journal entry** composed of two or more **postings** whose signed amounts sum to exactly zero. Wallet balances are a maintained projection of those postings, not the source of truth.

## The model

| Table | Role |
|---|---|
| `ledger_account` | One per wallet (`WALLET_LIABILITY`), plus system clearing accounts |
| `journal_entry` | The header: type, status, positive amount, initiator |
| `ledger_posting` | The lines: account, direction, amount, signed amount |

A transfer of 30.50:

| Account | Direction | Amount (minor) | Signed |
|---|---|---|---|
| Sender's wallet | DEBIT | 3050 | −3050 |
| Recipient's wallet | CREDIT | 3050 | +3050 |
|  |  | **Sum** | **0** |

`LedgerService.post` computes that sum before writing anything and throws `LedgerImbalanceException` if it is non-zero. One check, in one place, makes "money cannot be created or destroyed" a property of the system rather than something each feature has to remember.

## Deposits and withdrawals balance too

A deposit could have been modelled as a single credit. It is not, because a one-sided entry breaks the invariant everywhere else depends on, and the check would have to carry an exception for it.

Instead a deposit credits the wallet and debits a `CASH_IN_CLEARING` system account — money moving from the outside world onto the platform's balance sheet. In production that account is where a card processor or bank rail settles. Withdrawals mirror it against `CASH_OUT_CLEARING`. The books close.

## Why the direction lives on the posting

The journal entry carries a strictly positive amount and a type. Direction is entirely a property of each posting. Storing a signed amount on the header as well would create two sources of truth for the same fact, and they would eventually disagree.

`signed_minor` on the posting **is** derived from direction and amount, which looks like the same mistake. It is stored anyway so the zero-sum invariant can be checked with a plain `SUM()` in SQL, and a `CHECK` constraint ties the two together so the derived column cannot rot.

## Balances as a projection

Summing every posting on each read would be correct and would get slower forever. `wallet.balance_minor` is therefore maintained in the same transaction that writes the postings — there is no window in which a posting exists without its matching balance change.

A cached value is only trustworthy if something checks it. Two views exist for that:

- `unbalanced_journal_entry` — any row means an entry does not sum to zero
- `wallet_balance_drift` — any row means a cached balance disagrees with its postings

`LedgerReconciliationIT` queries these views directly after a busy sequence of movements, rather than re-implementing the check in Java. An admin endpoint exposes the same comparison for one wallet.

## Failures leave a trace

A refused transfer writes a `FAILED` journal entry and an audit event in a **separate** transaction. The transaction that failed is about to roll back and would otherwise take the record of the failure with it, leaving nothing to explain a support ticket.

## Consequences

- Three rows per movement instead of one balance update. The write cost is real and worth it.
- Reversals are new compensating entries, never edits or deletes. The ledger is append-only.
- The reconciliation views make correctness observable rather than asserted.
- More code than `balance += amount`. That is the point: the extra code is the part that makes the balance defensible.
