package com.souhajaidi.wallet.transaction.api.dto;

import com.souhajaidi.wallet.ledger.domain.JournalEntry;
import java.time.Instant;
import java.util.UUID;

/** A completed movement of money, as returned by the API. */
public record TransactionResponse(
        UUID id,
        String type,
        String status,
        String amount,
        long amountMinor,
        String currency,
        String reference,
        String failureReason,
        Instant createdAt) {

    public static TransactionResponse from(JournalEntry entry) {
        return new TransactionResponse(
                entry.getId(),
                entry.getEntryType().name(),
                entry.getStatus().name(),
                entry.amount().toMajor().toPlainString(),
                entry.getAmountMinor(),
                entry.getCurrency(),
                entry.getReference(),
                entry.getFailureReason(),
                entry.getCreatedAt());
    }
}
