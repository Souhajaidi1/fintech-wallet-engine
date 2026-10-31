package com.souhajaidi.wallet.ledger.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** One side of the ledger: either a wallet's liability account or a system clearing account. */
@Entity
@Table(name = "ledger_account")
public class LedgerAccount {

    @Id
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(name = "account_type", nullable = false)
    private AccountType accountType;

    @Column(name = "wallet_id")
    private UUID walletId;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    protected LedgerAccount() {
        // for JPA
    }

    private LedgerAccount(AccountType accountType, UUID walletId, String currency) {
        this.id = UUID.randomUUID();
        this.accountType = accountType;
        this.walletId = walletId;
        this.currency = currency.toUpperCase();
    }

    public static LedgerAccount forWallet(UUID walletId, String currency) {
        return new LedgerAccount(AccountType.WALLET_LIABILITY, walletId, currency);
    }

    public static LedgerAccount system(AccountType accountType, String currency) {
        if (accountType == AccountType.WALLET_LIABILITY) {
            throw new IllegalArgumentException("WALLET_LIABILITY accounts must be created with forWallet");
        }
        return new LedgerAccount(accountType, null, currency);
    }

    public UUID getId() {
        return id;
    }

    public AccountType getAccountType() {
        return accountType;
    }

    public UUID getWalletId() {
        return walletId;
    }

    public String getCurrency() {
        return currency;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
