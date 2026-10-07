package com.healthdocs.backend.adapter.out.auth;

import com.healthdocs.backend.adapter.out.persistence.AppUserRepository;
import com.healthdocs.backend.adapter.out.persistence.entity.AppUserEntity;
import com.healthdocs.backend.application.port.out.AuthProvider;
import com.healthdocs.backend.application.port.out.InvalidAuthenticationTokenException;
import com.healthdocs.backend.domain.model.Role;
import com.healthdocs.backend.infrastructure.config.AuthConfigurationProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.stereotype.Component;

/** Validates Supabase access tokens using standard JWT/JWKS and resolves the linked business account. */
@Component
@ConditionalOnProperty(name = "app.auth-provider", havingValue = "supabase")
public class SupabaseAuthProvider implements AuthProvider {

    private static final String PROVIDER = "supabase";

    private final AppUserRepository users;
    private final JwtDecoder jwtDecoder;
    private final String issuer;

    public SupabaseAuthProvider(
            AppUserRepository users,
            JwtDecoder jwtDecoder,
            AuthConfigurationProperties properties) {
        this.users = users;
        this.jwtDecoder = jwtDecoder;
        this.issuer = properties.externalJwt().issuerUrl();
    }

    @Override
    public AuthenticatedUser authenticate(String email, String password) {
        throw new ResponseStatusException(
                HttpStatus.GONE, "Use Supabase Auth from the frontend; this endpoint does not accept credentials");
    }

    @Override
    public AuthenticatedUser authenticateToken(String token) {
        final Jwt jwt;
        try {
            jwt = jwtDecoder.decode(token);
        } catch (JwtException | IllegalArgumentException exception) {
            throw new InvalidAuthenticationTokenException();
        }

        String subject = jwt.getSubject();
        if (subject == null || subject.isBlank()) {
            throw new InvalidAuthenticationTokenException();
        }
        AppUserEntity user = users.findByAuthIdentity(PROVIDER, issuer, subject)
                .orElseThrow(InvalidAuthenticationTokenException::new);
        Role role = user.getRole();
        return new AuthenticatedUser(user.getId(), user.getEmail(), role, user.isEnabled());
    }

    @Override
    public String issueToken(AuthenticatedUser user) {
        throw new IllegalStateException("Supabase access tokens are issued by Supabase Auth");
    }
}
