package com.souhajaidi.wallet.wallet.domain;

public enum WalletStatus {
    /** Accepts deposits, withdrawals and transfers. */
    ACTIVE,
    /** Readable, but no money may move in or out. */
    FROZEN,
    /** Terminal state. Must hold a zero balance before it can be reached. */
    CLOSED
}
