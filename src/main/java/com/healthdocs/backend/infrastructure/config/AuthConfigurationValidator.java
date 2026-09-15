package com.healthdocs.backend.infrastructure.config;

import java.util.Locale;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;

/** Validates provider selection before the application becomes ready. */
@Configuration
@EnableConfigurationProperties(AuthConfigurationProperties.class)
public class AuthConfigurationValidator implements InitializingBean {

    private final AuthConfigurationProperties properties;
    private final Environment environment;

    public AuthConfigurationValidator(AuthConfigurationProperties properties, Environment environment) {
        this.properties = properties;
        this.environment = environment;
    }

    @Override
    public void afterPropertiesSet() {
        String provider = required(properties.provider(), "AUTH_PROVIDER").toLowerCase(Locale.ROOT);
        switch (provider) {
            case "local" -> validateLocal();
            case "cognito" -> validateCognito();
            case "disabled" -> {}
            default -> throw new IllegalStateException(
                    "AUTH_PROVIDER debe ser 'local', 'cognito' o 'disabled'; se recibió: " + properties.provider());
        }
    }

    private void validateLocal() {
        if (!environment.acceptsProfiles(Profiles.of("local", "dev"))) {
            throw new IllegalStateException("AUTH_PROVIDER=local solo se permite con el perfil Spring local o dev");
        }
        AuthConfigurationProperties.Jwt jwt = properties.jwt();
        if (jwt == null) {
            throw new IllegalStateException("JWT_SECRET, JWT_ISSUER y JWT_EXPIRATION son obligatorios para AUTH_PROVIDER=local");
        }
        String secret = required(jwt.secret(), "JWT_SECRET");
        if (secret.length() < 32) {
            throw new IllegalStateException("JWT_SECRET debe tener al menos 32 caracteres");
        }
        required(jwt.issuer(), "JWT_ISSUER");
        if (jwt.expiration() == null || jwt.expiration().isZero() || jwt.expiration().isNegative()) {
            throw new IllegalStateException("JWT_EXPIRATION debe ser una duración ISO-8601 positiva, por ejemplo PT1H");
        }
    }

    private void validateCognito() {
        AuthConfigurationProperties.Cognito cognito = properties.cognito();
        if (cognito == null) {
            throw new IllegalStateException(
                    "COGNITO_REGION, COGNITO_USER_POOL_ID y COGNITO_CLIENT_ID son obligatorios para AUTH_PROVIDER=cognito");
        }
        required(cognito.region(), "COGNITO_REGION");
        required(cognito.userPoolId(), "COGNITO_USER_POOL_ID");
        required(cognito.clientId(), "COGNITO_CLIENT_ID");
    }

    private static String required(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(name + " es obligatorio para el proveedor seleccionado");
        }
        return value;
    }
}
