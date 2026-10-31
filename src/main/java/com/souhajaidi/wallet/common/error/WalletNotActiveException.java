package com.souhajaidi.wallet.common.error;

import org.springframework.http.HttpStatus;

public class WalletNotActiveException extends BusinessException {

    public WalletNotActiveException(String status) {
        super(HttpStatus.CONFLICT, "WALLET_NOT_ACTIVE", "The wallet is " + status + " and cannot move money.");
    }
}
