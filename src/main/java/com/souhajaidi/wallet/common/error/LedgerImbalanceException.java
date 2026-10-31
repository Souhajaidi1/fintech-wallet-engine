package com.souhajaidi.wallet.common.error;

import org.springframework.http.HttpStatus;

/**
 * Raised when a journal entry is assembled whose postings do not sum to zero.
 *
 * <p>This is a programming error rather than a user error. It is checked before
 * the entry is written so an unbalanced entry can never reach the database.
 */
public class LedgerImbalanceException extends BusinessException {

    public LedgerImbalanceException(long imbalanceMinor) {
        super(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "LEDGER_IMBALANCE",
                "Refused to post an unbalanced journal entry (imbalance: " + imbalanceMinor + ").");
    }
}
