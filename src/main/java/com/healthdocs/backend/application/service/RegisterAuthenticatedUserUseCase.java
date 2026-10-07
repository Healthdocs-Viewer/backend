package com.healthdocs.backend.application.service;

import com.healthdocs.backend.adapter.out.persistence.AppUserRepository;
import com.healthdocs.backend.adapter.out.persistence.AuthIdentityRepository;
import com.healthdocs.backend.adapter.out.persistence.AuthRegistrationLock;
import com.healthdocs.backend.adapter.out.persistence.entity.AppUserEntity;
import com.healthdocs.backend.adapter.out.persistence.entity.AuthIdentityEntity;
import com.healthdocs.backend.application.port.out.ExternalIdentityTokenVerifier;
import com.healthdocs.backend.application.port.out.ExternalIdentityTokenVerifier.VerifiedExternalIdentity;
import com.healthdocs.backend.domain.model.Role;
import java.util.Locale;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Creates the Healthdocs account for an already authenticated external identity. */
@Service
@ConditionalOnProperty(name = "app.auth-provider", havingValue = "supabase")
public class RegisterAuthenticatedUserUseCase {

    private final AppUserRepository users;
    private final AuthIdentityRepository identities;
    private final AuthRegistrationLock registrationLock;
    private final ExternalIdentityTokenVerifier tokenVerifier;

    public RegisterAuthenticatedUserUseCase(
            AppUserRepository users,
            AuthIdentityRepository identities,
            AuthRegistrationLock registrationLock,
            ExternalIdentityTokenVerifier tokenVerifier) {
        this.users = users;
        this.identities = identities;
        this.registrationLock = registrationLock;
        this.tokenVerifier = tokenVerifier;
    }

    @Transactional
    public RegisteredUser execute(String accessToken) {
        VerifiedExternalIdentity identity = tokenVerifier.verify(accessToken);
        String normalizedEmail = identity.email().trim().toLowerCase(Locale.ROOT);

        // Serialize requests for the same provider identity. A retry or a concurrent
        // duplicate then observes the first committed link and returns that same user.
        registrationLock.lock("identity\n" + identity.provider() + "\n"
                + identity.issuer() + "\n" + identity.subject());
        var existingIdentity = identities.findByProviderAndIssuerAndSubject(
                identity.provider(), identity.issuer(), identity.subject());
        if (existingIdentity.isPresent()) {
            return view(existingIdentity.get().getUser());
        }

        // Different identities cannot race past the case-insensitive email check.
        registrationLock.lock("email\n" + normalizedEmail);
        if (users.findByEmailIgnoreCase(normalizedEmail).isPresent()) {
            throw new BusinessRuleViolationException("An account with this email already exists");
        }

        // external_subject is retained for legacy local-auth compatibility. The provider
        // identity itself is stored in auth_identity, which is the source of truth here.
        final AppUserEntity user;
        try {
            user = users.saveAndFlush(new AppUserEntity(
                    UUID.randomUUID().toString(), normalizedEmail, Role.PATIENT));
        } catch (DataIntegrityViolationException exception) {
            // Keep the database unique constraint as the final guard against a writer
            // that does not participate in the advisory-lock protocol.
            throw new BusinessRuleViolationException("An account with this email already exists");
        }
        try {
            identities.saveAndFlush(new AuthIdentityEntity(
                    user, identity.provider(), identity.issuer(), identity.subject()));
        } catch (DataIntegrityViolationException exception) {
            throw new BusinessRuleViolationException("This identity or email is already registered");
        }

        return view(user);
    }

    private static RegisteredUser view(AppUserEntity user) {
        return new RegisteredUser(user.getId(), user.getEmail(), user.getRole().value(), user.isEnabled());
    }

    public record RegisteredUser(UUID id, String email, String role, boolean enabled) {
    }
}
