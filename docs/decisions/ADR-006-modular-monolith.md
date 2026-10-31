# ADR-006 — Modular monolith, not microservices

**Status:** accepted · **Date:** 2026-09

## Context

A wallet platform is a common microservices example: an auth service, a wallet service, a ledger service, a transaction service, an API gateway, service discovery. Splitting this project that way would have made it look more sophisticated.

## Decision

One deployable Spring Boot application, packaged by feature: `iam`, `wallet`, `ledger`, `transaction`, `idempotency`, `audit`. Each owns its controllers, services, entities and repositories.

## Why not microservices

**A transfer is one transaction.** It writes a journal entry, two postings and two balance updates, and all of them must commit or none. Across service boundaries that becomes a distributed transaction — sagas, compensating actions, and a window where the money has left one wallet and not arrived in the other. The entire correctness argument in ADR-004 and ADR-005 rests on a single database transaction. Splitting the services would mean giving that up and rebuilding it, worse, in application code.

**The locking strategy would stop working.** `SELECT ... FOR UPDATE` on both wallets in one transaction is not available if the wallets live behind different services with different databases. The replacement is a distributed lock, which is a new component with its own failure modes and its own correctness proof.

**There is no independent scaling need.** Microservices earn their operational cost by letting parts scale and deploy separately. With one developer and no traffic, that cost buys nothing.

**Fake microservices are worse than an honest monolith.** Three Spring Boot apps sharing one database, called through a gateway that adds a network hop, is a distributed monolith: all the operational complexity, none of the isolation. A reviewer who has run these in production recognises the pattern immediately, and it reads as cargo-culting rather than judgement.

## Why packaging by feature rather than by layer

The alternative layout — `controller/`, `service/`, `repository/`, `entity/` at the top — means changing how transfers work touches four directories, and nothing in the structure records that `Wallet` and `LedgerAccount` belong together while `Wallet` and `User` do not.

Packaging by feature keeps a change local, makes the boundaries visible in the directory tree, and means that if one module ever does need to be extracted, the seam is already drawn.

Within a feature the layering is conventional: `api` → `service` → `repository` → `domain`.

## Consequences

- One deployment unit; one database; one transaction boundary.
- Module boundaries are a convention rather than a compile-time barrier. Java's module system or ArchUnit rules would enforce them; noted, not done.
- Extracting a module later means finding every cross-module call. Packaging by feature makes those visible.

## What would change this

Genuinely divergent scaling — a ledger read path handling orders of magnitude more traffic than writes — or separate teams owning separate parts. Neither applies here, and adopting the pattern before the problem exists is how projects acquire complexity they never grow into.
