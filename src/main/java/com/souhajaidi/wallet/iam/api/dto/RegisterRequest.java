package com.souhajaidi.wallet.iam.api.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @NotBlank @Email @Size(max = 320) String email,
        @NotBlank @Size(min = 12, max = 100, message = "password must be at least 12 characters") String password,
        @NotBlank @Size(max = 120) String fullName) {}
