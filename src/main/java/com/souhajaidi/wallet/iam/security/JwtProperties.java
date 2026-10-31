package com.souhajaidi.wallet.iam.security;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * JWT settings. The signing secret has no default on purpose: the application
 * refuses to start without one rather than falling back to a value that would be
 * identical in every checkout of this repository.
 */
@Validated
@ConfigurationProperties(prefix = "wallet.jwt")
public record JwtProperties(@NotBlank String secret, @Positive Duration ttl, @NotBlank String issuer) {}
