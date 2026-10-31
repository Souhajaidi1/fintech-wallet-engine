package com.souhajaidi.wallet.wallet.domain;

import com.souhajaidi.wallet.common.error.InsufficientFundsException;
import com.souhajaidi.wallet.common.error.WalletNotActiveException;
import com.souhajaidi.wallet.common.money.Money;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;

/**
 * A user's balance in one currency.
 *
 * <p>{@code balanceMinor} is a maintained projection of the ledger, not the source
 * of truth: the authoritative balance is the sum of this wallet's ledger postings.
 * The column exists so that reading a balance is a single indexed row read rather
 * than an aggregate over the posting history, and it is updated inside the same
 * database transaction that writes those postings. The {@code wallet_balance_drift}
 * view exists to prove the two never diverge.
 *
 * <p>The {@code @Version} column is a second line of defence. Money movement takes a
 * pessimistic row lock (ADR-004), so the optimistic check should never fire; if it
 * ever does, it means a write path skipped the lock, and failing loudly is correct.
 */
@Entity
@Table(name = "wallet")
public class Wallet {

    @Id
    private UUID id;

    @Column(name = "owner_id", nullable = false)
    private UUID ownerId;

    @Column(nullable = false)
    private String label;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(name = "balance_minor", nullable = false)
    private long balanceMinor;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private WalletStatus status = WalletStatus.ACTIVE;

    @Version
    @Column(nullable = false)
    private long version;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    protected Wallet() {
        // for JPA
    }

    public Wallet(UUID ownerId, String label, String currency) {
        this.id = UUID.randomUUID();
        this.ownerId = ownerId;
        this.label = label;
        this.currency = currency.toUpperCase();
        this.balanceMinor = 0L;
    }

    /**
     * Adds a signed amount to the balance, rejecting the change if it would take the
     * wallet negative. This is the only way the balance is allowed to move, so the
     * overdraft check cannot be forgotten at a call site.
     */
    public void applySignedAmount(Money signedAmount) {
        requireActive();
        if (!currency.equals(signedAmount.currency())) {
            throw new IllegalArgumentException(
                    "wallet holds " + currency + " but the posting is in " + signedAmount.currency());
        }

        long updated = Math.addExact(balanceMinor, signedAmount.minorUnits());
        if (updated < 0L) {
            throw new InsufficientFundsException();
        }
        this.balanceMinor = updated;
    }

    public void requireActive() {
        if (status != WalletStatus.ACTIVE) {
            throw new WalletNotActiveException(status.name());
        }
    }

    public void freeze() {
        this.status = WalletStatus.FROZEN;
    }

    public void unfreeze() {
        this.status = WalletStatus.ACTIVE;
    }

    public Money balance() {
        return Money.ofMinor(balanceMinor, currency);
    }

    public UUID getId() {
        return id;
    }

    public UUID getOwnerId() {
        return ownerId;
    }

    public String getLabel() {
        return label;
    }

    public String getCurrency() {
        return currency;
    }

    public long getBalanceMinor() {
        return balanceMinor;
    }

    public WalletStatus getStatus() {
        return status;
    }

    public long getVersion() {
        return version;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
