package com.souhajaidi.wallet.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.souhajaidi.wallet.iam.repository.UserRepository;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

class SecurityIT extends AbstractIntegrationTest {

    @Autowired
    private UserRepository users;

    private String uniqueEmail() {
        return "user-" + UUID.randomUUID() + "@example.test";
    }

    @Test
    @DisplayName("wallet endpoints reject an anonymous caller")
    void requiresAuthentication() throws Exception {
        MvcResult result =
                mockMvc.perform(get("/api/v1/wallets/{id}", UUID.randomUUID())).andReturn();
        assertThat(result.getResponse().getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED.value());
    }

    @Test
    @DisplayName("a forged or tampered token is rejected")
    void rejectsForgedToken() throws Exception {
        String token = api.registerAndLogin(uniqueEmail(), "correct-horse-battery");
        String tampered = token.substring(0, token.length() - 4) + "AAAA";

        MvcResult result = mockMvc.perform(get("/api/v1/wallets").header("Authorization", "Bearer " + tampered))
                .andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED.value());
    }

    @Test
    @DisplayName("one user cannot read another user's wallet, and cannot tell it exists")
    void cannotReadSomeoneElsesWallet() throws Exception {
        String owner = api.registerAndLogin(uniqueEmail(), "correct-horse-battery");
        String stranger = api.registerAndLogin(uniqueEmail(), "correct-horse-battery");

        UUID wallet = api.createWallet(owner, "USD");
        MvcResult result = api.getWallet(stranger, wallet);

        // 404 rather than 403: a 403 would confirm the id exists.
        assertThat(result.getResponse().getStatus()).isEqualTo(HttpStatus.NOT_FOUND.value());
    }

    @Test
    @DisplayName("one user cannot move money out of another user's wallet")
    void cannotSpendSomeoneElsesWallet() throws Exception {
        String owner = api.registerAndLogin(uniqueEmail(), "correct-horse-battery");
        String attacker = api.registerAndLogin(uniqueEmail(), "correct-horse-battery");

        UUID victim = api.createWallet(owner, "USD");
        UUID attackerWallet = api.createWallet(attacker, "USD");
        api.deposit(owner, victim, "500.00", UUID.randomUUID().toString());

        MvcResult result = api.transfer(attacker, victim, attackerWallet, "500.00", UUID.randomUUID().toString());

        assertThat(result.getResponse().getStatus()).isEqualTo(HttpStatus.NOT_FOUND.value());
        assertThat(api.balanceMinor(owner, victim)).isEqualTo(50_000L);
    }

    @Test
    @DisplayName("a non-admin cannot reach admin endpoints")
    void adminEndpointsAreRestricted() throws Exception {
        String token = api.registerAndLogin(uniqueEmail(), "correct-horse-battery");
        UUID wallet = api.createWallet(token, "USD");

        MvcResult result = mockMvc.perform(get("/api/v1/admin/ledger/wallets/{id}/reconciliation", wallet)
                        .header("Authorization", "Bearer " + token))
                .andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(HttpStatus.FORBIDDEN.value());
    }

    @Test
    @DisplayName("passwords are stored only as a bcrypt hash")
    void passwordsAreHashed() throws Exception {
        String email = uniqueEmail();
        api.registerAndLogin(email, "correct-horse-battery");

        var stored = users.findByEmailIgnoreCase(email).orElseThrow();
        assertThat(stored.getPasswordHash()).startsWith("$2");
        assertThat(stored.getPasswordHash()).doesNotContain("correct-horse-battery");
    }

    @Test
    @DisplayName("a failed login does not reveal whether the account exists")
    void loginFailureIsIndistinguishable() throws Exception {
        String email = uniqueEmail();
        api.registerAndLogin(email, "correct-horse-battery");

        MvcResult wrongPassword = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"wrong-password-here\"}"))
                .andReturn();

        MvcResult unknownUser = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"nobody-" + UUID.randomUUID()
                                + "@example.test\",\"password\":\"wrong-password-here\"}"))
                .andReturn();

        assertThat(wrongPassword.getResponse().getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED.value());
        assertThat(unknownUser.getResponse().getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED.value());
        assertThat(api.read(wrongPassword).get("detail").asText())
                .isEqualTo(api.read(unknownUser).get("detail").asText());
    }
}
