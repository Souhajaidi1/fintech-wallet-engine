package com.souhajaidi.wallet.common.error;

import org.springframework.http.HttpStatus;

/**
 * Raised when an authenticated user references a wallet they do not own.
 *
 * <p>Returns 404 rather than 403 on purpose: a 403 would confirm that the wallet
 * id exists, which lets an attacker map the id space by probing.
 */
public class ForbiddenWalletAccessException extends BusinessException {

    public ForbiddenWalletAccessException() {
        super(HttpStatus.NOT_FOUND, "WALLET_NOT_FOUND", "No wallet with that id.");
    }
}
