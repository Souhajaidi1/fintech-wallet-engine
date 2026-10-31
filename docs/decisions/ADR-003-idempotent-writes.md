# ADR-003 — Idempotent write endpoints

**Status:** accepted · **Date:** 2026-09

## Context

HTTP gives no way to distinguish "the request failed" from "the response was lost". A client that posts a transfer and sees a timeout has no idea whether the money moved. It will retry. On a mobile network it will retry often.

## Decision

`POST` endpoints that move money accept an `Idempotency-Key` header. The key is claimed by inserting a row whose uniqueness is enforced by the database; a duplicate replays the stored response instead of re-running the operation.

## Why a unique constraint rather than a lookup

The obvious implementation — check whether the key exists, and if not, insert it and proceed — is itself a race:

```
T1: SELECT ... WHERE key = 'abc'   -> no rows
T2: SELECT ... WHERE key = 'abc'   -> no rows
T1: INSERT; transfer 50.00
T2: INSERT; transfer 50.00         <- 100.00 moved
```

Every request therefore attempts the `INSERT` first and treats a unique-violation as "someone else owns this key". PostgreSQL arbitrates, and exactly one request wins regardless of interleaving. The check and the claim become a single atomic act.

## Scope of the key

Unique on `(key_value, user_id, endpoint)`, not globally:

- **user_id** — one client's choice of key cannot collide with another's.
- **endpoint** — the same key against a different operation is a different operation.

## The request fingerprint

A SHA-256 of the request body *and* the path variable is stored with the claim. On replay:

- same fingerprint → replay the stored response
- different fingerprint → `422 IDEMPOTENCY_KEY_REUSED`

Without the fingerprint, a client that reused a key for a genuinely different transfer would receive the first transfer's response and believe the second had succeeded. Including the path variable matters because hashing the body alone would let the same key deposit into a different wallet and pass as a retry.

## Transaction boundaries

Claim, complete and release each run `REQUIRES_NEW`. This is not decoration:

- the claim must be **visible to concurrent retries**, which means committed before the operation runs
- the claim must **survive the operation's rollback**, or a failure would erase the evidence that the key was used
- committing separately is what lets a crashed request leave an `IN_PROGRESS` row rather than silently freeing the key

These live on `IdempotencyStore`, a separate bean from `IdempotencyService`, because Spring applies `@Transactional` through a proxy — a call from one method of a bean to another method of the same bean bypasses it entirely and the propagation would be silently ignored.

## Failure handling

If the operation throws, the claim is **released**. Otherwise a transfer refused for insufficient funds would burn its key, and a corrected retry with the same key would be rejected as a duplicate. Only successful operations leave a permanent claim.

## Consequences

- One extra round trip to the database per protected write.
- The table grows without bound; keys are never expired. A scheduled cleanup with a documented retention window is needed before this faces real traffic, and is listed as a known limit.
- A request that crashes mid-flight leaves its key claimed as `IN_PROGRESS` forever, so retries get `409` until an operator intervenes. A lease with a timeout is the standard fix.
