package com.souhajaidi.wallet.common.config;

import com.fasterxml.jackson.databind.SerializationFeature;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class JacksonConfig {

    /**
     * Writes {@code BigDecimal} in plain notation instead of scientific notation.
     *
     * <p>Amounts leave this API as JSON strings, not JSON numbers: a JSON number
     * becomes a double in most clients, which would undo the exactness the ledger
     * is built on the moment the response is parsed. The DTOs enforce that; this
     * setting covers any decimal that reaches the wire by another route.
     */
    @Bean
    public Jackson2ObjectMapperBuilderCustomizer exactMonetaryOutput() {
        return builder -> {
            builder.featuresToEnable(SerializationFeature.WRITE_BIGDECIMAL_AS_PLAIN);
            builder.featuresToDisable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        };
    }
}
