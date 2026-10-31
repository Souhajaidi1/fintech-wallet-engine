package com.souhajaidi.wallet.common.error;

import org.springframework.http.HttpStatus;

/** Raised when a known idempotency key is replayed with a different request body. */
public class IdempotencyConflictException extends BusinessException {

    public IdempotencyConflictException() {
        super(
                HttpStatus.UNPROCESSABLE_ENTITY,
                "IDEMPOTENCY_KEY_REUSED",
                "This Idempotency-Key was already used for a different request body.");
    }
}
