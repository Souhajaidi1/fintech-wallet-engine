package com.souhajaidi.wallet.ledger.domain;

public enum AccountType {
    /**
     * The money the platform owes one wallet holder. Credited when the wallet
     * receives money, debited when it sends money.
     */
    WALLET_LIABILITY,
    /**
     * Counterparty for deposits. Represents money arriving from the outside world;
     * in a real system this is where a card processor or bank rail would settle.
     */
    CASH_IN_CLEARING,
    /** Counterparty for withdrawals. Represents money leaving for an external rail. */
    CASH_OUT_CLEARING
}
