# ADR-004 — Pessimistic locking for money movement

**Status:** accepted · **Date:** 2026-09

## Context

A wallet holds 100.00. Two withdrawals of 80.00 arrive in the same millisecond.

```
T1: read balance -> 100.00
T2: read balance -> 100.00
T1: 80 <= 100, ok. write balance = 20.00
T2: 80 <= 100, ok. write balance = 20.00   <- 160.00 withdrawn from 100.00
```

This is a lost update. It is not an exotic interleaving: it is what a user double-tapping a button produces, and it stays invisible in testing until it happens with real money.

## Decision

Money movement opens by taking `PESSIMISTIC_WRITE` row locks (`SELECT ... FOR UPDATE`) on every wallet involved, **in a deterministic order**, before reading any balance. All invariant checks are then made against locked state.

## Why pessimistic rather than optimistic

Optimistic locking — read a version, write conditionally, retry on conflict — was the alternative, and it is the better choice for most CRUD.

It is the worse choice here:

**Contention is concentrated by design.** Payroll runs, refund batches and popular merchant accounts all pile onto a small number of hot wallets. Optimistic control degrades exactly where the load is: as contention rises, so does the conflict rate, and with it the retry rate, which raises contention further. The failure mode is a retry storm at the worst possible moment.

**A retry is not free when money is involved.** Every optimistic retry re-runs the transaction. Deciding whether re-running is safe means reasoning about idempotency at a second layer, underneath the one that already exists at the API boundary.

**Blocking is the honest behaviour.** A transfer that waits 5ms for a lock is correct. A transfer that fails and asks the client to try again has pushed a correctness problem outward into every caller.

The trade is throughput on a hot wallet for simplicity and predictability. For a wallet, that is the right way round.

## Deterministic lock ordering

A transfer locks two rows. Two transfers in opposite directions between the same pair could deadlock:

```
T1 (A -> B): locks A, waits for B
T2 (B -> A): locks B, waits for A
```

Both wait forever until the database kills one. The fix is that every transaction takes locks in the same sequence, so the cycle cannot form. `WalletRepository.lockAllById` loads both rows in one query with `ORDER BY w.id`, and callers sort the ids first. `ConcurrencyIT.oppositeDirectionTransfersDoNotDeadlock` fires fifty interleaved transfers in both directions and asserts none fail.

## Defence in depth

The lock is the primary mechanism. Two more sit underneath it:

1. **`@Version` on `Wallet`.** Money movement always takes the row lock, so the optimistic check should never fire. If it ever does, a write path skipped the lock — failing loudly is the correct response to a bug of that kind.
2. **`CHECK (balance_minor >= 0)`.** Even if every line of application logic were bypassed, PostgreSQL refuses to store a negative balance.

An invariant this important should not rest on one mechanism being correct.

## Consequences

- Transactions holding locks must stay short. No external calls, no user interaction, no long computation between lock and commit.
- Hikari's connection timeout is set to 5s, so a pathological lock wait becomes a fast error rather than a request that ties up a pool connection indefinitely.
- Throughput per wallet is bounded by lock hold time. Acceptable at this scale; account sharding is the standard remedy beyond it.
- `ConcurrencyIT.concurrentTransfersCannotOverdraw` fires twenty simultaneous transfers of 10.00 at a wallet holding 100.00 and asserts that exactly ten succeed and the balance lands on zero.
