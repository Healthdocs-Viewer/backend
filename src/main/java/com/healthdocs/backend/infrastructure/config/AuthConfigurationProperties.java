package com.healthdocs.backend.infrastructure.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "auth")
public record AuthConfigurationProperties(
        String provider,
        Jwt jwt,
        Cognito cognito) {

    public record Jwt(String secret, String issuer, Duration expiration) {
    }

    public record Cognito(String region, String userPoolId, String clientId) {
    }
}
