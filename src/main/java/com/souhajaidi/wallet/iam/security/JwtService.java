package com.souhajaidi.wallet.iam.security;

import com.souhajaidi.wallet.iam.domain.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;
import java.util.UUID;
import javax.crypto.SecretKey;
import org.springframework.stereotype.Service;

/** Issues and verifies the stateless access tokens used by the API. */
@Service
public class JwtService {

    private static final String CLAIM_ROLE = "role";

    private final SecretKey key;
    private final JwtProperties properties;

    public JwtService(JwtProperties properties) {
        this.properties = properties;
        byte[] secret = properties.secret().getBytes(StandardCharsets.UTF_8);
        if (secret.length < 32) {
            throw new IllegalStateException("wallet.jwt.secret must be at least 32 bytes for HS256");
        }
        this.key = Keys.hmacShaKeyFor(secret);
    }

    public String issue(User user) {
        Instant now = Instant.now();
        return Jwts.builder()
                .issuer(properties.issuer())
                .subject(user.getId().toString())
                .claim(CLAIM_ROLE, user.getRole().name())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(properties.ttl())))
                .signWith(key)
                .compact();
    }

    public long ttlSeconds() {
        return properties.ttl().toSeconds();
    }

    /**
     * Verifies signature, issuer and expiry, returning the authenticated principal.
     * A malformed, expired or foreign-signed token yields an empty result rather
     * than an exception, so the filter chain can treat it as simply unauthenticated.
     */
    public Optional<AuthenticatedUser> verify(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(key)
                    .requireIssuer(properties.issuer())
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();

            return Optional.of(
                    new AuthenticatedUser(UUID.fromString(claims.getSubject()), claims.get(CLAIM_ROLE, String.class)));
        } catch (JwtException | IllegalArgumentException ex) {
            return Optional.empty();
        }
    }

    /** The identity carried by a verified token. */
    public record AuthenticatedUser(UUID userId, String role) {}
}
