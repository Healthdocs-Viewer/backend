package com.healthdocs.backend.adapter.out.auth;

import com.healthdocs.backend.application.port.out.ExternalIdentityTokenVerifier;
import com.healthdocs.backend.application.port.out.InvalidAuthenticationTokenException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.stereotype.Component;

/** Supabase adapter for the provider-neutral registration identity contract. */
@Component
@ConditionalOnProperty(name = "app.auth-provider", havingValue = "supabase")
public class SupabaseIdentityTokenVerifier implements ExternalIdentityTokenVerifier {

    private static final String PROVIDER = "supabase";

    private final JwtDecoder jwtDecoder;

    public SupabaseIdentityTokenVerifier(JwtDecoder jwtDecoder) {
        this.jwtDecoder = jwtDecoder;
    }

    @Override
    public VerifiedExternalIdentity verify(String accessToken) {
        if (accessToken == null || accessToken.isBlank()) {
            throw new InvalidAuthenticationTokenException();
        }

        final Jwt jwt;
        try {
            jwt = jwtDecoder.decode(accessToken);
        } catch (JwtException | IllegalArgumentException exception) {
            throw new InvalidAuthenticationTokenException();
        }

        String issuer = jwt.getIssuer() == null ? null : jwt.getIssuer().toString();
        String subject = jwt.getSubject();
        String email = jwt.getClaimAsString("email");
        if (issuer == null || issuer.isBlank()
                || subject == null || subject.isBlank()
                || email == null || email.isBlank()) {
            throw new InvalidAuthenticationTokenException();
        }

        return new VerifiedExternalIdentity(PROVIDER, issuer, subject, email);
    }
}
