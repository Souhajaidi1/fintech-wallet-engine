# ADR-002 — Money as integer minor units

**Status:** accepted · **Date:** 2026-09

## Context

Every amount in the system needs a representation that is exact, comparable, storable and serialisable without loss.

## Decision

A `Money` value object wrapping a `long` count of minor units plus an ISO-4217 currency code. Stored as `BIGINT`. Never `float`, never `double`, anywhere.

## Why not floating point

`0.1 + 0.2` is `0.30000000000000004` in IEEE-754. Neither 0.1 nor 0.2 is representable in binary, so every arithmetic step introduces error. In a wallet the error compounds silently until reconciliation fails and nobody can say when the drift began. `MoneyTest` pins this: a thousand additions of 0.01 must total exactly 10.00.

## Why minor units rather than `BigDecimal`

`BigDecimal` is exact, so it would work. Minor units were chosen because:

**Scale is a decision `BigDecimal` forces you to make repeatedly, and forget once.** `new BigDecimal("10.00")` and `BigDecimal.valueOf(10)` are `equals`-unequal despite being numerically identical, because `equals` compares scale. That distinction bites in maps, sets and assertions. With `long`, 1000 is 1000.

**Every `divide` needs a rounding mode or it throws at runtime.** A `MathContext` forgotten in one branch is a production incident. Integers have no such trap; where division is genuinely needed the rounding decision has to be made explicitly and visibly.

**The database type is native.** `BIGINT` sums, compares and indexes exactly, and `CHECK (balance_minor >= 0)` is a plain integer comparison. `NUMERIC` works but is heavier, and JPA's mapping introduces another place scale can be lost.

**JSON is safer.** A `BigDecimal` serialised as a JSON number becomes a double in most clients, undoing the exactness at the last step. Amounts leave this API as decimal *strings*, with `balanceMinor` alongside for clients that prefer integers.

## Design details

- `SCALE = 2`. Currencies with other exponents (JPY has 0, KWD has 3) would need a per-currency scale; noted as a limit rather than pretended away.
- `Money.ofMajor` **rejects** more precision than the currency allows. A request for `1.005` returns 400 rather than being rounded — silently rounding a user's money is how money goes missing.
- Arithmetic uses `Math.addExact` / `Math.subtractExact`, so overflow throws instead of wrapping. `long` covers roughly ±92 quadrillion minor units; wrapping at the boundary would be catastrophic and silent.
- Operations between different currencies throw `CurrencyMismatchException`. There is no implicit conversion.

## Consequences

- Callers must think in minor units internally. Contained: conversion happens once at the API boundary.
- A currency with a different exponent needs work before it can be supported.
- The type system prevents mixing currencies, which is a class of bug that cannot occur.
