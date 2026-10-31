package com.souhajaidi.wallet.transaction.api.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.UUID;

public record TransferRequest(
        @NotNull UUID sourceWalletId,
        @NotNull UUID destinationWalletId,
        @NotNull @DecimalMin(value = "0.01", message = "amount must be at least 0.01")
                @Digits(integer = 15, fraction = 2, message = "amount may have at most 2 decimal places")
                BigDecimal amount,
        @Size(max = 140) String reference) {}
