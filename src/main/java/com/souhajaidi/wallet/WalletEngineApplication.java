package com.souhajaidi.wallet;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class WalletEngineApplication {

    public static void main(String[] args) {
        SpringApplication.run(WalletEngineApplication.class, args);
    }
}
