package com.souhajaidi.wallet.idempotency.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;
import org.springframework.stereotype.Service;

/**
 * Makes a write endpoint safe to retry.
 *
 * <p>The problem this solves is not theoretical. A client posts a transfer, the server
 * completes it, and the response is lost to a dropped connection or a mobile network
 * handover. The client cannot distinguish a lost response from a failed request, so it
 * retries. Without a guard, the money moves twice.
 *
 * <p>The mechanism is a unique constraint, not a lookup-then-insert. Checking whether a
 * key exists and then inserting it is itself a race: two concurrent retries can both
 * find nothing and both proceed. Instead every request tries to INSERT the key and lets
 * the database arbitrate — the unique index on (key, user, endpoint) means exactly one
 * INSERT survives, whatever the interleaving.
 *
 * <p>Four outcomes:
 * <ul>
 *   <li>key is new — claim it, run the operation, store the response</li>
 *   <li>key held, COMPLETED, same body — replay the stored response without re-running</li>
 *   <li>key held, IN_PROGRESS — the original is still running; 409, retry shortly</li>
 *   <li>key held, different body — the client reused a key; 422, refuse</li>
 * </ul>
 *
 * <p>The key is scoped to (value, user, endpoint) rather than being globally unique so
 * that one client's choice of key cannot collide with another's, and so the same key
 * used against a different endpoint is a different operation.
 */
@Service
public class IdempotencyService {

    private final IdempotencyStore store;
    private final ObjectMapper objectMapper;

    public IdempotencyService(IdempotencyStore store, ObjectMapper objectMapper) {
        this.store = store;
        this.objectMapper = objectMapper;
    }

    /**
     * Runs {@code operation} at most once for the given key.
     *
     * @param key the client's Idempotency-Key; null or blank runs without protection
     * @param responseType the type a stored response is replayed as
     */
    public <T> Outcome<T> execute(
            String key,
            UUID userId,
            String endpoint,
            Object requestBody,
            Class<T> responseType,
            Supplier<T> operation) {

        if (key == null || key.isBlank()) {
            return new Outcome<>(operation.get(), false);
        }

        String hash = fingerprint(requestBody);
        Optional<UUID> claimId = store.claim(key, userId, endpoint, hash);

        if (claimId.isEmpty()) {
            // Either a genuine retry, or this request lost the INSERT race to a
            // concurrent twin. Both are answered from what the winner recorded.
            return new Outcome<>(store.replay(key, userId, endpoint, hash, responseType), true);
        }

        T result;
        try {
            result = operation.get();
        } catch (RuntimeException ex) {
            // The operation failed, so nothing happened that a retry would duplicate.
            // Free the key rather than leaving it claimed forever.
            store.release(claimId.get());
            throw ex;
        }

        store.complete(claimId.get(), 200, result);
        return new Outcome<>(result, false);
    }

    private String fingerprint(Object requestBody) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(objectMapper.writeValueAsBytes(requestBody)));
        } catch (NoSuchAlgorithmException | JsonProcessingException ex) {
            throw new IllegalStateException("could not fingerprint the request body", ex);
        }
    }

    /**
     * The result of an idempotent call.
     *
     * @param replayed true when this is the stored answer to an earlier identical request
     */
    public record Outcome<T>(T result, boolean replayed) {}
}
