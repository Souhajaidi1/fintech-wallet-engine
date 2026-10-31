package com.souhajaidi.wallet.idempotency.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * A client-supplied key claiming one logical operation.
 *
 * <p>{@code requestHash} is a SHA-256 of the request body. It is what distinguishes an
 * honest retry (same key, same body, replay the stored answer) from a client bug
 * (same key, different body, refuse). Without it, a client that reuses a key for a
 * different transfer would silently receive the earlier transfer's response and
 * believe the second one succeeded.
 */
@Entity
@Table(name = "idempotency_key")
public class IdempotencyKey {

    @Id
    private UUID id;

    @Column(name = "key_value", nullable = false)
    private String keyValue;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(nullable = false)
    private String endpoint;

    @Column(name = "request_hash", nullable = false, length = 64)
    private String requestHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private IdempotencyStatus status;

    @Column(name = "response_status")
    private Integer responseStatus;

    @Column(name = "response_body", columnDefinition = "text")
    private String responseBody;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "completed_at")
    private Instant completedAt;

    protected IdempotencyKey() {
        // for JPA
    }

    public IdempotencyKey(String keyValue, UUID userId, String endpoint, String requestHash) {
        this.id = UUID.randomUUID();
        this.keyValue = keyValue;
        this.userId = userId;
        this.endpoint = endpoint;
        this.requestHash = requestHash;
        this.status = IdempotencyStatus.IN_PROGRESS;
    }

    public void complete(int responseStatus, String responseBody) {
        this.status = IdempotencyStatus.COMPLETED;
        this.responseStatus = responseStatus;
        this.responseBody = responseBody;
        this.completedAt = Instant.now();
    }

    public boolean matches(String candidateHash) {
        return requestHash.equals(candidateHash);
    }

    public boolean isCompleted() {
        return status == IdempotencyStatus.COMPLETED;
    }

    public UUID getId() {
        return id;
    }

    public String getKeyValue() {
        return keyValue;
    }

    public UUID getUserId() {
        return userId;
    }

    public String getEndpoint() {
        return endpoint;
    }

    public IdempotencyStatus getStatus() {
        return status;
    }

    public Integer getResponseStatus() {
        return responseStatus;
    }

    public String getResponseBody() {
        return responseBody;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }
}
