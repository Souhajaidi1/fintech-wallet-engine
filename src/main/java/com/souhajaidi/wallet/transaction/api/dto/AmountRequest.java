package com.souhajaidi.wallet.transaction.api.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

/**
 * A deposit or withdrawal amount.
 *
 * <p>The amount is a {@link BigDecimal} on the way in, not a double, and it is bounded
 * to two decimal places by {@code @Digits} so a request for 10.005 is rejected at the
 * edge rather than rounded somewhere deeper in the stack.
 */
public record AmountRequest(
        @NotNull @DecimalMin(value = "0.01", message = "amount must be at least 0.01")
                @Digits(integer = 15, fraction = 2, message = "amount may have at most 2 decimal places")
                BigDecimal amount,
        @Size(max = 140) String reference) {}
