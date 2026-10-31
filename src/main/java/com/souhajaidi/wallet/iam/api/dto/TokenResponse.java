package com.souhajaidi.wallet.iam.api.dto;

public record TokenResponse(String accessToken, String tokenType, long expiresInSeconds) {

    public static TokenResponse bearer(String token, long expiresInSeconds) {
        return new TokenResponse(token, "Bearer", expiresInSeconds);
    }
}
