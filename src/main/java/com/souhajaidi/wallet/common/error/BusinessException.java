package com.souhajaidi.wallet.common.error;

import org.springframework.http.HttpStatus;

/**
 * Base class for errors that are part of the API contract: they carry a stable
 * machine-readable code and a status, and their message is safe to return to
 * the caller. Anything not derived from this class is treated as an internal
 * fault and never leaks its message.
 */
public abstract class BusinessException extends RuntimeException {

    private final HttpStatus status;
    private final String code;

    protected BusinessException(HttpStatus status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public HttpStatus status() {
        return status;
    }

    public String code() {
        return code;
    }
}
