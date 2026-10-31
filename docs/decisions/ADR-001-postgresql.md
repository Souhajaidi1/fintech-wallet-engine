# ADR-001 — PostgreSQL as the datastore

**Status:** accepted · **Date:** 2026-09

## Context

The system's central obligation is that a balance is always exactly the sum of the movements behind it. Two requests may touch the same wallet at the same moment. A single transfer must change two accounts or neither.

## Decision

PostgreSQL 16, as the single source of truth, with the schema owned by Flyway.

## Why

**ACID transactions are the feature, not a checkbox.** A transfer writes a journal entry, two postings and two balance updates. Any store where those five writes could partially land would need compensating logic to unwind partial failures — and that logic would itself need to be correct under concurrency.

**`SELECT ... FOR UPDATE` is what makes the overdraft check meaningful.** Without row-level locking the balance check is advisory: two transactions read the same value and both pass it. This is the mechanism ADR-004 is built on, and it has to exist in the database, not the application.

**Constraints are a second line of defence that no bug can bypass.** `CHECK (balance_minor >= 0)` and the unique index on idempotency keys hold whatever the application does. A store without them puts every invariant at the mercy of one careless code path.

**`BIGINT` is exact.** Minor units need integer arithmetic that does not drift (ADR-002). A store that represents numbers as doubles would undermine the design at its foundation.

## Alternatives considered

**MySQL/MariaDB.** Would work: InnoDB has row locking and ACID. PostgreSQL was chosen for stricter default behaviour (no silent truncation), partial indexes — used here to enforce one ledger account per wallet while allowing many system accounts — and richer CHECK constraint support.

**MongoDB.** Multi-document transactions exist but are a later addition rather than the model's centre of gravity. A ledger is inherently relational: entries reference accounts, postings reference entries, and every query is a join. Modelling it as documents would mean either duplicating data or reimplementing joins in application code.

**Event store with a rebuilt read model.** Conceptually close to what the ledger already is, and a good fit at much larger scale. Rejected as over-engineering for this project: it would add eventual consistency between writing an entry and being able to read a balance, which is a real cost paid for scale that does not exist here.

## Consequences

- Throughput on a single hot wallet is bounded by lock contention. Acceptable; sharding hot accounts is the known remedy.
- The application requires a live PostgreSQL to run at all — integration tests use Testcontainers rather than an in-memory substitute, which is slower but tests what actually ships.
- Schema changes go through versioned Flyway migrations. Hibernate runs with `ddl-auto: validate` and is never permitted to alter the schema.

## What would change this

A requirement to serve balances across regions with sub-50ms reads everywhere would force a rethink toward a distributed SQL store (CockroachDB, Spanner) rather than away from SQL entirely.
