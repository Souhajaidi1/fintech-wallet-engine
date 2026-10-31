package com.souhajaidi.wallet.idempotency.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.souhajaidi.wallet.common.error.IdempotencyConflictException;
import com.souhajaidi.wallet.common.error.RequestInFlightException;
import com.souhajaidi.wallet.idempotency.domain.IdempotencyKey;
import com.souhajaidi.wallet.idempotency.repository.IdempotencyKeyRepository;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Persistence for idempotency claims.
 *
 * <p>This is a separate bean from {@link IdempotencyService} rather than a few private
 * methods on it, because every method here needs {@code REQUIRES_NEW} to take effect.
 * Spring applies {@code @Transactional} through a proxy, so a call from one method of a
 * bean to another method of the same bean bypasses it entirely — the annotation would
 * be silently ignored and the claim would join the caller's transaction, which is
 * exactly the behaviour the mechanism must not have. Crossing a bean boundary is what
 * makes the propagation real.
 */
@Component
public class IdempotencyStore {

    private static final Logger log = LoggerFactory.getLogger(IdempotencyStore.class);

    private final IdempotencyKeyRepository keys;
    private final ObjectMapper objectMapper;

    public IdempotencyStore(IdempotencyKeyRepository keys, ObjectMapper objectMapper) {
        this.keys = keys;
        this.objectMapper = objectMapper;
    }

    /**
     * Attempts to claim a key by inserting it, letting the unique index arbitrate.
     *
     * <p>Committed independently so that concurrent retries can see the claim, and so a
     * crashed request leaves an IN_PROGRESS row rather than freeing the key.
     *
     * @return the claim id, or empty if another request already holds this key
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Optional<UUID> claim(String key, UUID userId, String endpoint, String requestHash) {
        try {
            IdempotencyKey saved = keys.saveAndFlush(new IdempotencyKey(key, userId, endpoint, requestHash));
            return Optional.of(saved.getId());
        } catch (DataIntegrityViolationException ex) {
            log.debug("Idempotency key already claimed for endpoint {}", endpoint);
            return Optional.empty();
        }
    }

    /**
     * Returns the stored response for a key that is already held.
     *
     * @throws IdempotencyConflictException if the key was reused with a different body
     * @throws RequestInFlightException if the original request has not finished
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public <T> T replay(String key, UUID userId, String endpoint, String requestHash, Class<T> responseType) {
        IdempotencyKey existing = keys.findByKeyValueAndUserIdAndEndpoint(key, userId, endpoint)
                .orElseThrow(() -> new IllegalStateException("idempotency key vanished between insert and read"));

        if (!existing.matches(requestHash)) {
            throw new IdempotencyConflictException();
        }
        if (!existing.isCompleted()) {
            throw new RequestInFlightException();
        }

        try {
            return objectMapper.readValue(existing.getResponseBody(), responseType);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("stored idempotent response could not be read back", ex);
        }
    }

    /** Stores the response of a completed operation so later retries can replay it. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void complete(UUID claimId, int status, Object result) {
        IdempotencyKey claim = keys.findById(claimId)
                .orElseThrow(() -> new IllegalStateException("idempotency claim " + claimId + " disappeared"));
        try {
            claim.complete(status, objectMapper.writeValueAsString(result));
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("could not store the idempotent response", ex);
        }
        keys.save(claim);
    }

    /**
     * Releases a claim whose operation failed, so the client can correct the request and
     * retry with the same key. Without this, a transfer rejected for insufficient funds
     * would burn its key and a later legitimate retry would be refused as a duplicate.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void release(UUID claimId) {
        keys.findById(claimId).ifPresent(keys::delete);
    }
}
