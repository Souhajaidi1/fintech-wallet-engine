package com.souhajaidi.wallet.iam.security;

import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/** Reads the authenticated user's id out of the security context. */
@Component
public class CurrentUser {

    public UUID requireId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof UUID userId)) {
            throw new IllegalStateException("no authenticated user on a secured endpoint");
        }
        return userId;
    }

    public boolean isAdmin() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null
                && authentication.getAuthorities().stream()
                        .anyMatch(granted -> "ROLE_ADMIN".equals(granted.getAuthority()));
    }
}
