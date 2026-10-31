package com.souhajaidi.wallet.integration;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.persistence.EntityManager;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

/**
 * Proves the ledger's two headline invariants against the database itself, using the
 * reconciliation views defined in the migrations rather than re-implementing the check
 * in test code.
 */
class LedgerReconciliationIT extends AbstractIntegrationTest {

    @Autowired
    private EntityManager entityManager;

    private String uniqueEmail() {
        return "user-" + UUID.randomUUID() + "@example.test";
    }

    @Test
    @Transactional
    @DisplayName("after a busy sequence of movements, every journal entry still sums to zero")
    void everyEntryBalances() throws Exception {
        String alice = api.registerAndLogin(uniqueEmail(), "correct-horse-battery");
        String bob = api.registerAndLogin(uniqueEmail(), "correct-horse-battery");

        UUID aliceWallet = api.createWallet(alice, "USD");
        UUID bobWallet = api.createWallet(bob, "USD");

        api.deposit(alice, aliceWallet, "250.00", UUID.randomUUID().toString());
        api.transfer(alice, aliceWallet, bobWallet, "75.25", UUID.randomUUID().toString());
        api.withdraw(bob, bobWallet, "25.25", UUID.randomUUID().toString());
        api.transfer(bob, bobWallet, aliceWallet, "10.00", UUID.randomUUID().toString());

        List<?> unbalanced = entityManager
                .createNativeQuery("SELECT journal_entry_id FROM unbalanced_journal_entry")
                .getResultList();

        assertThat(unbalanced).isEmpty();
    }

    @Test
    @Transactional
    @DisplayName("no wallet's cached balance drifts from its ledger postings")
    void cachedBalancesMatchTheLedger() throws Exception {
        String alice = api.registerAndLogin(uniqueEmail(), "correct-horse-battery");
        String bob = api.registerAndLogin(uniqueEmail(), "correct-horse-battery");

        UUID aliceWallet = api.createWallet(alice, "EUR");
        UUID bobWallet = api.createWallet(bob, "EUR");

        api.deposit(alice, aliceWallet, "500.00", UUID.randomUUID().toString());
        for (int i = 0; i < 15; i++) {
            api.transfer(alice, aliceWallet, bobWallet, "1.11", UUID.randomUUID().toString());
        }
        api.withdraw(bob, bobWallet, "5.00", UUID.randomUUID().toString());

        List<?> drifted = entityManager
                .createNativeQuery("SELECT wallet_id FROM wallet_balance_drift")
                .getResultList();

        assertThat(drifted).isEmpty();

        // 500.00 - (15 x 1.11) = 483.35, and 1.11 x 15 = 16.65 exactly, not 16.649999...
        assertThat(api.balanceMinor(alice, aliceWallet)).isEqualTo(48_335L);
        assertThat(api.balanceMinor(bob, bobWallet)).isEqualTo(1_165L);
    }
}
