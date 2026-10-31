package com.souhajaidi.wallet.idempotency.domain;

public enum IdempotencyStatus {
    /** Claimed by a request that has not finished yet. */
    IN_PROGRESS,
    /** The original request finished and its response is stored for replay. */
    COMPLETED
}
