package com.souhajaidi.wallet.common.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    static final String BEARER_SCHEME = "bearerAuth";

    @Bean
    public OpenAPI walletEngineOpenApi() {
        SecurityScheme bearer = new SecurityScheme()
                .type(SecurityScheme.Type.HTTP)
                .scheme("bearer")
                .bearerFormat("JWT")
                .description("Paste the token returned by POST /api/v1/auth/login.");

        return new OpenAPI()
                .info(new Info()
                        .title("Fintech Wallet Engine")
                        .version("1.0.0")
                        .description(
                                """
                                A digital wallet backend built on a double-entry ledger.

                                Money is held as integer minor units. Every movement is a balanced
                                journal entry. Write endpoints accept an `Idempotency-Key` header so
                                a retried request never moves money twice.

                                Start with `POST /api/v1/auth/register`, then `POST /api/v1/auth/login`,
                                then authorise with the returned token.
                                """)
                        .license(new License().name("MIT")))
                .components(new Components().addSecuritySchemes(BEARER_SCHEME, bearer))
                .addSecurityItem(new SecurityRequirement().addList(BEARER_SCHEME));
    }
}
