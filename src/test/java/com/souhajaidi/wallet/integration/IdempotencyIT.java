package com.souhajaidi.wallet.integration;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.MvcResult;

class IdempotencyIT extends AbstractIntegrationTest {

    private String uniqueEmail() {
        return "user-" + UUID.randomUUID() + "@example.test";
    }

    @Test
    @DisplayName("replaying a transfer with the same key moves the money once")
    void retryDoesNotDuplicateTransfer() throws Exception {
        String sender = api.registerAndLogin(uniqueEmail(), "correct-horse-battery");
        String recipient = api.registerAndLogin(uniqueEmail(), "correct-horse-battery");

        UUID from = api.createWallet(sender, "USD");
        UUID to = api.createWallet(recipient, "USD");
        api.deposit(sender, from, "100.00", UUID.randomUUID().toString());

        String key = UUID.randomUUID().toString();
        MvcResult first = api.transfer(sender, from, to, "40.00", key);
        MvcResult replay = api.transfer(sender, from, to, "40.00", key);

        assertThat(first.getResponse().getStatus()).isEqualTo(HttpStatus.CREATED.value());
        assertThat(first.getResponse().getHeader("Idempotent-Replay")).isEqualTo("false");

        assertThat(replay.getResponse().getStatus()).isEqualTo(HttpStatus.OK.value());
        assertThat(replay.getResponse().getHeader("Idempotent-Replay")).isEqualTo("true");

        // The replay returns the original transaction, not a new one.
        assertThat(api.read(replay).get("id").asText()).isEqualTo(api.read(first).get("id").asText());

        assertThat(api.balanceMinor(sender, from)).isEqualTo(6000L);
        assertThat(api.balanceMinor(recipient, to)).isEqualTo(4000L);
    }

    @Test
    @DisplayName("reusing a key with a different body is refused rather than replayed")
    void refusesKeyReuseWithDifferentBody() throws Exception {
        String sender = api.registerAndLogin(uniqueEmail(), "correct-horse-battery");
        String recipient = api.registerAndLogin(uniqueEmail(), "correct-horse-battery");

        UUID from = api.createWallet(sender, "USD");
        UUID to = api.createWallet(recipient, "USD");
        api.deposit(sender, from, "100.00", UUID.randomUUID().toString());

        String key = UUID.randomUUID().toString();
        api.transfer(sender, from, to, "10.00", key);
        MvcResult reused = api.transfer(sender, from, to, "25.00", key);

        assertThat(reused.getResponse().getStatus()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY.value());
        assertThat(api.read(reused).get("code").asText()).isEqualTo("IDEMPOTENCY_KEY_REUSED");

        // Only the first transfer happened.
        assertThat(api.balanceMinor(sender, from)).isEqualTo(9000L);
    }

    @Test
    @DisplayName("a key freed by a failed transfer can be reused once the problem is fixed")
    void failedOperationReleasesItsKey() throws Exception {
        String sender = api.registerAndLogin(uniqueEmail(), "correct-horse-battery");
        String recipient = api.registerAndLogin(uniqueEmail(), "correct-horse-battery");

        UUID from = api.createWallet(sender, "USD");
        UUID to = api.createWallet(recipient, "USD");
        api.deposit(sender, from, "10.00", UUID.randomUUID().toString());

        String key = UUID.randomUUID().toString();
        MvcResult refused = api.transfer(sender, from, to, "50.00", key);
        assertThat(refused.getResponse().getStatus()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY.value());

        api.deposit(sender, from, "100.00", UUID.randomUUID().toString());
        MvcResult retried = api.transfer(sender, from, to, "50.00", key);

        assertThat(retried.getResponse().getStatus()).isEqualTo(HttpStatus.CREATED.value());
        assertThat(api.balanceMinor(recipient, to)).isEqualTo(5000L);
    }

    @Test
    @DisplayName("deposits carry the same protection as transfers")
    void depositIsIdempotent() throws Exception {
        String token = api.registerAndLogin(uniqueEmail(), "correct-horse-battery");
        UUID wallet = api.createWallet(token, "TND");

        String key = UUID.randomUUID().toString();
        api.deposit(token, wallet, "75.00", key);
        api.deposit(token, wallet, "75.00", key);
        api.deposit(token, wallet, "75.00", key);

        assertThat(api.balanceMinor(token, wallet)).isEqualTo(7500L);
    }
}
