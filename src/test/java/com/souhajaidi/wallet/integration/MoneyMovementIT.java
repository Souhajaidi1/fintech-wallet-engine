package com.souhajaidi.wallet.integration;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.MvcResult;

/** End-to-end financial correctness against a real PostgreSQL. */
class MoneyMovementIT extends AbstractIntegrationTest {

    private String uniqueEmail() {
        return "user-" + UUID.randomUUID() + "@example.test";
    }

    @Test
    @DisplayName("a transfer moves the exact amount and leaves the ledger balanced")
    void transferSucceeds() throws Exception {
        String sender = api.registerAndLogin(uniqueEmail(), "correct-horse-battery");
        String recipient = api.registerAndLogin(uniqueEmail(), "correct-horse-battery");

        UUID from = api.createWallet(sender, "USD");
        UUID to = api.createWallet(recipient, "USD");

        api.deposit(sender, from, "100.00", UUID.randomUUID().toString());
        MvcResult result = api.transfer(sender, from, to, "30.50", UUID.randomUUID().toString());

        assertThat(result.getResponse().getStatus()).isEqualTo(HttpStatus.CREATED.value());
        assertThat(api.balanceMinor(sender, from)).isEqualTo(6950L);
        assertThat(api.balanceMinor(recipient, to)).isEqualTo(3050L);
    }

    @Test
    @DisplayName("a transfer larger than the balance is refused and changes nothing")
    void insufficientFundsRollsBack() throws Exception {
        String sender = api.registerAndLogin(uniqueEmail(), "correct-horse-battery");
        String recipient = api.registerAndLogin(uniqueEmail(), "correct-horse-battery");

        UUID from = api.createWallet(sender, "USD");
        UUID to = api.createWallet(recipient, "USD");
        api.deposit(sender, from, "10.00", UUID.randomUUID().toString());

        MvcResult result = api.transfer(sender, from, to, "50.00", UUID.randomUUID().toString());

        assertThat(result.getResponse().getStatus()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY.value());
        assertThat(api.read(result).get("code").asText()).isEqualTo("INSUFFICIENT_FUNDS");

        // The postings written before the balance check must have rolled back with it.
        assertThat(api.balanceMinor(sender, from)).isEqualTo(1000L);
        assertThat(api.balanceMinor(recipient, to)).isZero();
    }

    @Test
    @DisplayName("a withdrawal cannot overdraw a wallet")
    void withdrawalCannotOverdraw() throws Exception {
        String token = api.registerAndLogin(uniqueEmail(), "correct-horse-battery");
        UUID wallet = api.createWallet(token, "EUR");
        api.deposit(token, wallet, "25.00", UUID.randomUUID().toString());

        MvcResult result = api.withdraw(token, wallet, "25.01", UUID.randomUUID().toString());

        assertThat(result.getResponse().getStatus()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY.value());
        assertThat(api.balanceMinor(token, wallet)).isEqualTo(2500L);
    }

    @Test
    @DisplayName("transfers between different currencies are refused")
    void refusesCrossCurrencyTransfer() throws Exception {
        String sender = api.registerAndLogin(uniqueEmail(), "correct-horse-battery");
        String recipient = api.registerAndLogin(uniqueEmail(), "correct-horse-battery");

        UUID usd = api.createWallet(sender, "USD");
        UUID eur = api.createWallet(recipient, "EUR");
        api.deposit(sender, usd, "100.00", UUID.randomUUID().toString());

        MvcResult result = api.transfer(sender, usd, eur, "10.00", UUID.randomUUID().toString());

        assertThat(result.getResponse().getStatus()).isEqualTo(HttpStatus.BAD_REQUEST.value());
        assertThat(api.balanceMinor(sender, usd)).isEqualTo(10_000L);
    }

    @Test
    @DisplayName("a wallet cannot transfer to itself")
    void refusesSelfTransfer() throws Exception {
        String token = api.registerAndLogin(uniqueEmail(), "correct-horse-battery");
        UUID wallet = api.createWallet(token, "USD");
        api.deposit(token, wallet, "100.00", UUID.randomUUID().toString());

        MvcResult result = api.transfer(token, wallet, wallet, "10.00", UUID.randomUUID().toString());

        assertThat(result.getResponse().getStatus()).isEqualTo(HttpStatus.BAD_REQUEST.value());
        assertThat(api.balanceMinor(token, wallet)).isEqualTo(10_000L);
    }

    @Test
    @DisplayName("a zero or negative amount is rejected at the edge")
    void rejectsInvalidAmounts() throws Exception {
        String token = api.registerAndLogin(uniqueEmail(), "correct-horse-battery");
        UUID wallet = api.createWallet(token, "USD");

        assertThat(api.deposit(token, wallet, "0.00", null).getResponse().getStatus())
                .isEqualTo(HttpStatus.BAD_REQUEST.value());
        assertThat(api.deposit(token, wallet, "-5.00", null).getResponse().getStatus())
                .isEqualTo(HttpStatus.BAD_REQUEST.value());
        assertThat(api.deposit(token, wallet, "1.005", null).getResponse().getStatus())
                .isEqualTo(HttpStatus.BAD_REQUEST.value());
        assertThat(api.balanceMinor(token, wallet)).isZero();
    }
}
