package com.souhajaidi.wallet.ledger.domain;

public enum EntryStatus {
    /** The entry and its postings are committed and balanced. */
    POSTED,
    /**
     * The movement was rejected. Recorded so that a refused transfer leaves a trace,
     * written in its own transaction so the rejection survives the rollback of the
     * movement itself.
     */
    FAILED
}
