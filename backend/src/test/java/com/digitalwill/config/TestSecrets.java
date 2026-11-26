package com.digitalwill.config;

import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.MapPropertySource;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;

public class TestSecrets implements ApplicationContextInitializer<ConfigurableApplicationContext> {
    @Override
    public void initialize(ConfigurableApplicationContext context) {
        byte[] key = new byte[32];
        new SecureRandom().nextBytes(key);
        context.getEnvironment().getPropertySources().addFirst(new MapPropertySource("testSecrets",
                Map.of("digitalwill.crypto.master-key", Base64.getEncoder().encodeToString(key),
                       "digitalwill.jobs.secret", UUID.randomUUID().toString())));
    }
}
