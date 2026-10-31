package com.souhajaidi.wallet.iam.api.dto;

import jakarta.validation.constraints.NotBlank;

public record LoginRequest(@NotBlank String email, @NotBlank String password) {}
