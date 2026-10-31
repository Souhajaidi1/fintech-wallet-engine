package com.souhajaidi.wallet.iam.domain;

public enum Role {
    /** Owns wallets and moves their own money. */
    USER,
    /** Reads any wallet and runs ledger reconciliation. Never moves another user's money. */
    ADMIN
}
