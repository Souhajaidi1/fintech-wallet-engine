package com.souhajaidi.wallet.wallet.api.dto;

import com.souhajaidi.wallet.wallet.domain.Wallet;
import java.time.Instant;
import java.util.UUID;

/**
 * A wallet as returned by the API.
 *
 * <p>{@code balance} is a decimal string rather than a JSON number: most clients parse
 * a JSON number into a double, which would reintroduce the rounding error the whole
 * ledger exists to avoid. {@code balanceMinor} is included alongside it for clients
 * that would rather do their own integer arithmetic.
 */
public record WalletResponse(
        UUID id,
        String label,
        String currency,
        String balance,
        long balanceMinor,
        String status,
        Instant createdAt) {

    public static WalletResponse from(Wallet wallet) {
        return new WalletResponse(
                wallet.getId(),
                wallet.getLabel(),
                wallet.getCurrency(),
                wallet.balance().toMajor().toPlainString(),
                wallet.getBalanceMinor(),
                wallet.getStatus().name(),
                wallet.getCreatedAt());
    }
}
