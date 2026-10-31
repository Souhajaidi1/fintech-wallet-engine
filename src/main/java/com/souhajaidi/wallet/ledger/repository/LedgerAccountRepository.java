package com.souhajaidi.wallet.ledger.repository;

import com.souhajaidi.wallet.ledger.domain.AccountType;
import com.souhajaidi.wallet.ledger.domain.LedgerAccount;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LedgerAccountRepository extends JpaRepository<LedgerAccount, UUID> {

    Optional<LedgerAccount> findByWalletId(UUID walletId);

    Optional<LedgerAccount> findByAccountTypeAndCurrency(AccountType accountType, String currency);
}
