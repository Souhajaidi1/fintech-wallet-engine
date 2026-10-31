package com.souhajaidi.wallet.transaction.api.dto;

import java.util.UUID;

/** Compares a wallet's cached balance against the sum of its ledger postings. */
public record ReconciliationResponse(
        UUID walletId, long cachedMinor, long ledgerMinor, long driftMinor, boolean reconciled) {

    public static ReconciliationResponse of(UUID walletId, long cachedMinor, long ledgerMinor) {
        long drift = cachedMinor - ledgerMinor;
        return new ReconciliationResponse(walletId, cachedMinor, ledgerMinor, drift, drift == 0L);
    }
}
