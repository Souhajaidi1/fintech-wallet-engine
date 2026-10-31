package com.souhajaidi.wallet.common.error;

import org.springframework.http.HttpStatus;

public class InvalidTransferException extends BusinessException {

    public InvalidTransferException(String message) {
        super(HttpStatus.BAD_REQUEST, "INVALID_TRANSFER", message);
    }
}
