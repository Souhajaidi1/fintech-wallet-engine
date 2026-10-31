package com.souhajaidi.wallet.ledger.domain;

public enum PostingDirection {
    /** Decreases a liability account. Money leaving a wallet. */
    DEBIT,
    /** Increases a liability account. Money arriving in a wallet. */
    CREDIT;

    public long applySign(long amountMinor) {
        return this == CREDIT ? amountMinor : -amountMinor;
    }
}
