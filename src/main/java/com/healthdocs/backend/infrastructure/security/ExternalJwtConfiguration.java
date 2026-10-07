package com.healthdocs.backend.infrastructure.security;

import com.healthdocs.backend.infrastructure.config.AuthConfigurationProperties;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;

/** Standards-based validation for access tokens issued by an external OIDC provider. */
@Configuration
@ConditionalOnProperty(name = "app.auth-provider", havingValue = "supabase")
public class ExternalJwtConfiguration {

    @Bean
    JwtDecoder externalJwtDecoder(AuthConfigurationProperties properties) {
        AuthConfigurationProperties.ExternalJwt external = properties.externalJwt();
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(external.jwksUrl())
                .jwsAlgorithms(algorithms -> {
                    algorithms.add(SignatureAlgorithm.ES256);
                    algorithms.add(SignatureAlgorithm.RS256);
                })
                .build();

        OAuth2TokenValidator<Jwt> audienceValidator = jwt -> {
            List<String> audiences = jwt.getAudience();
            return audiences != null && audiences.contains(external.audience())
                    ? OAuth2TokenValidatorResult.success()
                    : OAuth2TokenValidatorResult.failure(
                            new org.springframework.security.oauth2.core.OAuth2Error(
                                    "invalid_token", "Token audience is not allowed", null));
        };
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                JwtValidators.createDefaultWithIssuer(external.issuerUrl()), audienceValidator));
        return decoder;
    }
}
