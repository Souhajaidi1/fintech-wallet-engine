package com.souhajaidi.wallet.unit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.souhajaidi.wallet.common.money.CurrencyMismatchException;
import com.souhajaidi.wallet.common.money.Money;
import java.math.BigDecimal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class MoneyTest {

    @Test
    @DisplayName("addition is exact where floating point is not")
    void additionIsExact() {
        // The canonical demonstration: as doubles, 0.10 + 0.20 != 0.30.
        Money sum = Money.ofMajor(new BigDecimal("0.10"), "USD")
                .plus(Money.ofMajor(new BigDecimal("0.20"), "USD"));

        assertThat(sum.minorUnits()).isEqualTo(30L);
        assertThat(sum.toMajor()).isEqualByComparingTo(new BigDecimal("0.30"));
    }

    @Test
    @DisplayName("a thousand small additions do not drift")
    void repeatedAdditionDoesNotDrift() {
        Money running = Money.zero("EUR");
        for (int i = 0; i < 1000; i++) {
            running = running.plus(Money.ofMajor(new BigDecimal("0.01"), "EUR"));
        }
        assertThat(running.minorUnits()).isEqualTo(1000L);
    }

    @ParameterizedTest
    @ValueSource(strings = {"1.005", "0.001", "12.3456"})
    @DisplayName("sub-cent precision is rejected rather than rounded away")
    void rejectsExcessPrecision(String amount) {
        assertThatThrownBy(() -> Money.ofMajor(new BigDecimal(amount), "USD"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("more precision");
    }

    @Test
    @DisplayName("arithmetic across currencies is refused")
    void refusesMixedCurrencies() {
        assertThatThrownBy(() -> Money.ofMinor(100L, "USD").plus(Money.ofMinor(100L, "EUR")))
                .isInstanceOf(CurrencyMismatchException.class);
    }

    @Test
    @DisplayName("overflow raises rather than silently wrapping")
    void refusesOverflow() {
        Money huge = Money.ofMinor(Long.MAX_VALUE, "USD");
        assertThatThrownBy(() -> huge.plus(Money.ofMinor(1L, "USD"))).isInstanceOf(ArithmeticException.class);
    }

    @Test
    @DisplayName("negation and comparison behave as expected")
    void negationAndComparison() {
        Money ten = Money.ofMinor(1000L, "TND");
        assertThat(ten.negated().minorUnits()).isEqualTo(-1000L);
        assertThat(ten.negated().isNegative()).isTrue();
        assertThat(Money.ofMinor(500L, "TND").isLessThan(ten)).isTrue();
        assertThat(Money.zero("TND").isZero()).isTrue();
    }

    @Test
    @DisplayName("currency codes are normalised and validated")
    void validatesCurrency() {
        assertThat(Money.ofMinor(1L, "usd").currency()).isEqualTo("USD");
        assertThatThrownBy(() -> Money.ofMinor(1L, "DOLLAR")).isInstanceOf(IllegalArgumentException.class);
    }
}
