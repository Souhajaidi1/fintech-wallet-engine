package com.souhajaidi.wallet.common.money;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

/**
 * An exact amount of money, held as a signed count of minor units (cents,
 * fils, millimes) together with its ISO-4217 currency.
 *
 * <p>Binary floating point is never used anywhere in this codebase: {@code 0.1 + 0.2}
 * is not {@code 0.3} in a {@code double}, and a wallet that loses a millime per
 * thousand transfers is a wallet that fails reconciliation. Minor units are chosen
 * over {@link BigDecimal} because they map to a native {@code BIGINT} column, make
 * every arithmetic operation exact by construction, and remove the whole class of
 * scale and rounding-mode bugs that {@code BigDecimal} invites. See ADR-002.
 *
 * <p>Instances are immutable. Every operation returns a new instance and rejects
 * arithmetic between different currencies.
 */
public record Money(long minorUnits, String currency) implements Comparable<Money> {

    /** Number of minor units in one major unit. Fixed at 2 for the currencies supported here. */
    public static final int SCALE = 2;

    private static final BigDecimal MINOR_PER_MAJOR = BigDecimal.valueOf(100L);

    public Money {
        Objects.requireNonNull(currency, "currency must not be null");
        if (currency.length() != 3) {
            throw new IllegalArgumentException("currency must be a 3-letter ISO-4217 code: " + currency);
        }
        currency = currency.toUpperCase();
    }

    public static Money ofMinor(long minorUnits, String currency) {
        return new Money(minorUnits, currency);
    }

    public static Money zero(String currency) {
        return new Money(0L, currency);
    }

    /**
     * Builds an amount from a decimal major-unit value, for example {@code 12.34}.
     * The value must carry no more precision than the currency allows: {@code 12.345}
     * is rejected rather than silently rounded, because silently rounding a user's
     * money is how money goes missing.
     */
    public static Money ofMajor(BigDecimal major, String currency) {
        Objects.requireNonNull(major, "amount must not be null");
        BigDecimal minor = major.multiply(MINOR_PER_MAJOR);
        if (minor.stripTrailingZeros().scale() > 0) {
            throw new IllegalArgumentException(
                    "amount has more precision than " + currency + " supports: " + major.toPlainString());
        }
        return new Money(minor.longValueExact(), currency);
    }

    /** Renders the amount as a plain decimal string, for example {@code "12.34"}. */
    public BigDecimal toMajor() {
        return BigDecimal.valueOf(minorUnits).divide(MINOR_PER_MAJOR, SCALE, RoundingMode.UNNECESSARY);
    }

    public Money plus(Money other) {
        requireSameCurrency(other);
        return new Money(Math.addExact(minorUnits, other.minorUnits), currency);
    }

    public Money minus(Money other) {
        requireSameCurrency(other);
        return new Money(Math.subtractExact(minorUnits, other.minorUnits), currency);
    }

    public Money negated() {
        return new Money(Math.negateExact(minorUnits), currency);
    }

    public boolean isPositive() {
        return minorUnits > 0L;
    }

    public boolean isNegative() {
        return minorUnits < 0L;
    }

    public boolean isZero() {
        return minorUnits == 0L;
    }

    public boolean isLessThan(Money other) {
        return compareTo(other) < 0;
    }

    public boolean isGreaterThan(Money other) {
        return compareTo(other) > 0;
    }

    @Override
    public int compareTo(Money other) {
        requireSameCurrency(other);
        return Long.compare(minorUnits, other.minorUnits);
    }

    private void requireSameCurrency(Money other) {
        Objects.requireNonNull(other, "other amount must not be null");
        if (!currency.equals(other.currency)) {
            throw new CurrencyMismatchException(currency, other.currency);
        }
    }

    @Override
    public String toString() {
        return toMajor().toPlainString() + " " + currency;
    }
}
