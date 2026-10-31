package com.souhajaidi.wallet.common.error;

import org.springframework.http.HttpStatus;

public class EmailAlreadyRegisteredException extends BusinessException {

    public EmailAlreadyRegisteredException() {
        // Deliberately does not echo the address back: the response would otherwise
        // let an unauthenticated caller enumerate which emails hold an account.
        super(HttpStatus.CONFLICT, "EMAIL_ALREADY_REGISTERED", "That email cannot be registered.");
    }
}
