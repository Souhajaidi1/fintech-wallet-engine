package com.souhajaidi.wallet.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Base class for tests that exercise the real stack.
 *
 * <p>These run against a real PostgreSQL in a container rather than an in-memory
 * database. Almost everything this project claims to do — row locking, unique-constraint
 * arbitration, CHECK constraints, partial indexes — is behaviour of PostgreSQL itself.
 * An H2 test would pass while proving nothing about the guarantees the README advertises.
 *
 * <p>The container is static, so one database is started for the whole suite and reused
 * across classes. Flyway runs against it exactly as it does in production, which means
 * the migrations are tested on every build too.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
public abstract class AbstractIntegrationTest {

    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("wallet")
            .withUsername("wallet")
            .withPassword("wallet")
            .withReuse(true);

    static {
        POSTGRES.start();
    }

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("wallet.jwt.secret", () -> "test-only-secret-value-at-least-32-bytes-long");
    }

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected ObjectMapper objectMapper;

    protected ApiClient api;

    @BeforeEach
    void initApiClient() {
        api = new ApiClient(mockMvc, objectMapper);
    }
}
