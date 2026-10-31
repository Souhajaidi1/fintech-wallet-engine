package com.souhajaidi.wallet.integration;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * A thin client over MockMvc so the tests read as scenarios rather than as HTTP plumbing.
 * Every call goes through the real filter chain, so authentication and authorisation are
 * exercised on every request rather than being mocked away.
 */
class ApiClient {

    private final MockMvc mockMvc;
    private final ObjectMapper json;

    ApiClient(MockMvc mockMvc, ObjectMapper json) {
        this.mockMvc = mockMvc;
        this.json = json;
    }

    String registerAndLogin(String email, String password) throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(
                                new RegisterBody(email, password, "Test User"))))
                .andReturn();

        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(new LoginBody(email, password))))
                .andReturn();

        return read(result).get("accessToken").asText();
    }

    UUID createWallet(String token, String currency) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/wallets")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(
                                new CreateWalletBody("wallet-" + UUID.randomUUID(), currency))))
                .andReturn();

        return UUID.fromString(read(result).get("id").asText());
    }

    MvcResult deposit(String token, UUID walletId, String amount, String idempotencyKey) throws Exception {
        var request = post("/api/v1/wallets/{id}/deposit", walletId)
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(new AmountBody(new BigDecimal(amount), "test")));
        if (idempotencyKey != null) {
            request = request.header("Idempotency-Key", idempotencyKey);
        }
        return mockMvc.perform(request).andReturn();
    }

    MvcResult withdraw(String token, UUID walletId, String amount, String idempotencyKey) throws Exception {
        var request = post("/api/v1/wallets/{id}/withdraw", walletId)
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(new AmountBody(new BigDecimal(amount), "test")));
        if (idempotencyKey != null) {
            request = request.header("Idempotency-Key", idempotencyKey);
        }
        return mockMvc.perform(request).andReturn();
    }

    MvcResult transfer(String token, UUID from, UUID to, String amount, String idempotencyKey) throws Exception {
        var request = post("/api/v1/transfers")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(new TransferBody(from, to, new BigDecimal(amount), "test")));
        if (idempotencyKey != null) {
            request = request.header("Idempotency-Key", idempotencyKey);
        }
        return mockMvc.perform(request).andReturn();
    }

    long balanceMinor(String token, UUID walletId) throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/wallets/{id}/balance", walletId)
                        .header("Authorization", "Bearer " + token))
                .andReturn();
        return read(result).get("balanceMinor").asLong();
    }

    MvcResult getWallet(String token, UUID walletId) throws Exception {
        return mockMvc.perform(
                        get("/api/v1/wallets/{id}", walletId).header("Authorization", "Bearer " + token))
                .andReturn();
    }

    JsonNode read(MvcResult result) throws Exception {
        return json.readTree(result.getResponse().getContentAsString());
    }

    private record RegisterBody(String email, String password, String fullName) {}

    private record LoginBody(String email, String password) {}

    private record CreateWalletBody(String label, String currency) {}

    private record AmountBody(BigDecimal amount, String reference) {}

    private record TransferBody(UUID sourceWalletId, UUID destinationWalletId, BigDecimal amount, String reference) {}
}
