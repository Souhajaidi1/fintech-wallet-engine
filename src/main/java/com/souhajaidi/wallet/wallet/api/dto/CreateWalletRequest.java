package com.souhajaidi.wallet.wallet.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateWalletRequest(
        @NotBlank @Size(max = 60) String label,
        @NotBlank @Pattern(regexp = "^[A-Za-z]{3}$", message = "currency must be a 3-letter ISO-4217 code")
                String currency) {}
