package com.souhajaidi.wallet.common.error;

import java.util.UUID;
import org.springframework.http.HttpStatus;

public class WalletNotFoundException extends BusinessException {

    public WalletNotFoundException(UUID walletId) {
        super(HttpStatus.NOT_FOUND, "WALLET_NOT_FOUND", "No wallet with id " + walletId + ".");
    }
}
