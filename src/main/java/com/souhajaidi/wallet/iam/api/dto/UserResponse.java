package com.souhajaidi.wallet.iam.api.dto;

import com.souhajaidi.wallet.iam.domain.User;
import java.time.Instant;
import java.util.UUID;

public record UserResponse(UUID id, String email, String fullName, String role, Instant createdAt) {

    public static UserResponse from(User user) {
        return new UserResponse(
                user.getId(),
                user.getEmail(),
                user.getFullName(),
                user.getRole().name(),
                user.getCreatedAt());
    }
}
