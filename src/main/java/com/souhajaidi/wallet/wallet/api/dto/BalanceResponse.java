package com.souhajaidi.wallet.wallet.api.dto;

import com.souhajaidi.wallet.wallet.domain.Wallet;
import java.time.Instant;
import java.util.UUID;

public record BalanceResponse(
        UUID walletId, String currency, String balance, long balanceMinor, Instant asOf) {

    public static BalanceResponse from(Wallet wallet) {
        return new BalanceResponse(
                wallet.getId(),
                wallet.getCurrency(),
                wallet.balance().toMajor().toPlainString(),
                wallet.getBalanceMinor(),
                Instant.now());
    }
}
