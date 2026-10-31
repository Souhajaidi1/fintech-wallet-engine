package com.souhajaidi.wallet.common.error;

import org.springframework.http.HttpStatus;

/** Raised when a retry arrives while the original request is still being processed. */
public class RequestInFlightException extends BusinessException {

    public RequestInFlightException() {
        super(
                HttpStatus.CONFLICT,
                "REQUEST_IN_FLIGHT",
                "The original request with this Idempotency-Key is still in progress. Retry shortly.");
    }
}
