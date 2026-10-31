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
 * One line of a journal entry: an amount moving into or out of one ledger account.
 *
 * <p>{@code signedMinor} is derived from the direction and stored alongside it so the
 * zero-sum invariant can be checked with a plain {@code SUM()} in SQL. A database
 * CHECK constraint keeps the two consistent, so the derived column cannot rot.
 */
@Entity
@Table(name = "ledger_posting")
public class LedgerPosting {

    @Id
    private UUID id;

    @Column(name = "journal_entry_id", nullable = false)
    private UUID journalEntryId;

    @Column(name = "account_id", nullable = false)
    private UUID accountId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PostingDirection direction;

    @Column(name = "amount_minor", nullable = false)
    private long amountMinor;

    @Column(name = "signed_minor", nullable = false)
    private long signedMinor;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    protected LedgerPosting() {
        // for JPA
    }

    public LedgerPosting(UUID journalEntryId, UUID accountId, PostingDirection direction, Money amount) {
        if (!amount.isPositive()) {
            throw new IllegalArgumentException("a posting amount must be strictly positive");
        }
        this.id = UUID.randomUUID();
        this.journalEntryId = journalEntryId;
        this.accountId = accountId;
        this.direction = direction;
        this.amountMinor = amount.minorUnits();
        this.signedMinor = direction.applySign(amount.minorUnits());
    }

    public long getSignedMinor() {
        return signedMinor;
    }

    public UUID getId() {
        return id;
    }

    public UUID getJournalEntryId() {
        return journalEntryId;
    }

    public UUID getAccountId() {
        return accountId;
    }

    public PostingDirection getDirection() {
        return direction;
    }

    public long getAmountMinor() {
        return amountMinor;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
