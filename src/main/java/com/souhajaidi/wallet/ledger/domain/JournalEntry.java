package com.souhajaidi.wallet.ledger.domain;

import com.souhajaidi.wallet.common.money.Money;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * One movement of money, recorded as a header over two or more {@link LedgerPosting}
 * rows whose signed amounts sum to zero.
 *
 * <p>The header carries a single positive amount and a type; direction lives entirely
 * on the postings. Storing a signed amount here as well would create two sources of
 * truth for the same fact.
 */
@Entity
@Table(name = "journal_entry")
public class JournalEntry {

    @Id
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(name = "entry_type", nullable = false)
    private EntryType entryType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EntryStatus status;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(name = "amount_minor", nullable = false)
    private long amountMinor;

    @Column(name = "initiated_by", nullable = false)
    private UUID initiatedBy;

    @Column
    private String reference;

    @Column(name = "failure_reason")
    private String failureReason;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    protected JournalEntry() {
        // for JPA
    }

    private JournalEntry(
            EntryType entryType, EntryStatus status, Money amount, UUID initiatedBy, String reference, String failure) {
        if (!amount.isPositive()) {
            throw new IllegalArgumentException("a journal entry amount must be strictly positive");
        }
        this.id = UUID.randomUUID();
        this.entryType = entryType;
        this.status = status;
        this.currency = amount.currency();
        this.amountMinor = amount.minorUnits();
        this.initiatedBy = initiatedBy;
        this.reference = reference;
        this.failureReason = failure;
    }

    public static JournalEntry posted(EntryType type, Money amount, UUID initiatedBy, String reference) {
        return new JournalEntry(type, EntryStatus.POSTED, amount, initiatedBy, reference, null);
    }

    public static JournalEntry failed(EntryType type, Money amount, UUID initiatedBy, String reference, String reason) {
        return new JournalEntry(type, EntryStatus.FAILED, amount, initiatedBy, reference, reason);
    }

    public Money amount() {
        return Money.ofMinor(amountMinor, currency);
    }

    public UUID getId() {
        return id;
    }

    public EntryType getEntryType() {
        return entryType;
    }

    public EntryStatus getStatus() {
        return status;
    }

    public String getCurrency() {
        return currency;
    }

    public long getAmountMinor() {
        return amountMinor;
    }

    public UUID getInitiatedBy() {
        return initiatedBy;
    }

    public String getReference() {
        return reference;
    }

    public String getFailureReason() {
        return failureReason;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
