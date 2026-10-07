package com.healthdocs.backend.adapter.out.auth;

import com.healthdocs.backend.adapter.out.persistence.AppUserRepository;
import com.healthdocs.backend.adapter.out.persistence.AuthIdentityRepository;
import com.healthdocs.backend.adapter.out.persistence.entity.AppUserEntity;
import com.healthdocs.backend.adapter.out.persistence.entity.AuthIdentityEntity;
import com.healthdocs.backend.application.port.out.AuthProvider;
import com.healthdocs.backend.application.port.out.InvalidAuthenticationTokenException;
import com.healthdocs.backend.domain.model.Role;
import java.util.Locale;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import com.healthdocs.backend.infrastructure.security.JwtService;
import io.jsonwebtoken.JwtException;

@Component
@ConditionalOnProperty(name = "app.auth-provider", havingValue = "local", matchIfMissing = true)
public class LocalAuthProvider implements AuthProvider {

    private final AppUserRepository users;
    private final AuthIdentityRepository identities;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public LocalAuthProvider(
            AppUserRepository users,
            AuthIdentityRepository identities,
            PasswordEncoder passwordEncoder,
            JwtService jwtService) {
        this.users = users;
        this.identities = identities;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    @Override
    public AuthenticatedUser authenticate(String email, String password) {
        AppUserEntity user = users.findByEmailIgnoreCase(email.trim().toLowerCase(Locale.ROOT))
                .filter(candidate -> candidate.getPasswordHash() != null)
                .filter(candidate -> passwordEncoder.matches(password, candidate.getPasswordHash()))
                .orElseThrow(() -> new BadCredentialsException("Invalid credentials"));

        if (!user.isEnabled()) {
            throw new DisabledAccountException();
        }

        ensureLocalIdentity(user);
        Role role = user.getRole();
        return new AuthenticatedUser(user.getId(), user.getEmail(), role, true);
    }

    @Override
    public AuthenticatedUser authenticateToken(String token) {
        final JwtService.JwtIdentity identity;
        try {
            identity = jwtService.parse(token);
        } catch (JwtException | IllegalArgumentException exception) {
            throw new InvalidAuthenticationTokenException();
        }

        AppUserEntity user;
        if (identity.issuer() == null) {
            user = users.findById(identity.userId())
                    .orElseThrow(InvalidAuthenticationTokenException::new);
        } else {
            user = users.findByAuthIdentity("local", identity.issuer(), identity.subject())
                    .orElseThrow(InvalidAuthenticationTokenException::new);
        }
        if (!user.isEnabled()) {
            return new AuthenticatedUser(user.getId(), user.getEmail(), user.getRole(), false);
        }
        return new AuthenticatedUser(user.getId(), user.getEmail(), user.getRole(), true);
    }

    @Override
    public String issueToken(AuthenticatedUser user) {
        AppUserEntity account = users.findById(user.id())
                .orElseThrow(InvalidAuthenticationTokenException::new);
        ensureLocalIdentity(account);
        return jwtService.issue(user, account.getExternalSubject());
    }

    private void ensureLocalIdentity(AppUserEntity user) {
        identities.findByProviderAndIssuerAndSubject("local", jwtService.issuer(), user.getExternalSubject())
                .ifPresentOrElse(existing -> {
                    if (!existing.getUser().getId().equals(user.getId())) {
                        throw new IllegalStateException("Local authentication identity is linked to another user");
                    }
                }, () -> identities.save(new AuthIdentityEntity(
                        user, "local", jwtService.issuer(), user.getExternalSubject())));
    }

    public static class DisabledAccountException extends RuntimeException {
    }

}
