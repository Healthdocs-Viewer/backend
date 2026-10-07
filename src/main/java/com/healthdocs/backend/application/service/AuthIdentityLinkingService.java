package com.healthdocs.backend.application.service;

import com.healthdocs.backend.adapter.out.persistence.AppUserRepository;
import com.healthdocs.backend.adapter.out.persistence.AuthIdentityRepository;
import com.healthdocs.backend.adapter.out.persistence.entity.AuthIdentityEntity;
import java.util.Locale;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Trusted internal operation for provisioning a verified provider identity.
 * Callers must verify control of the provider identity and select the existing
 * business account by its internal UUID. This service intentionally has no HTTP endpoint.
 */
@Service
public class AuthIdentityLinkingService {

    private final AppUserRepository users;
    private final AuthIdentityRepository identities;

    public AuthIdentityLinkingService(AppUserRepository users, AuthIdentityRepository identities) {
        this.users = users;
        this.identities = identities;
    }

    @Transactional
    public AuthIdentityEntity linkVerifiedIdentity(
            UUID internalUserId,
            String provider,
            String issuer,
            String subject) {
        return linkVerifiedIdentity(internalUserId, provider, issuer, subject, null, null);
    }

    @Transactional
    public AuthIdentityEntity linkVerifiedIdentity(
            UUID internalUserId,
            String provider,
            String issuer,
            String subject,
            String linkedBy,
            String verificationReference) {
        if (internalUserId == null) {
            throw new IllegalArgumentException("Internal user ID is required");
        }
        String normalizedProvider = required(provider, "provider").toLowerCase(Locale.ROOT);
        String verifiedIssuer = required(issuer, "issuer");
        String verifiedSubject = required(subject, "subject");
        if ((linkedBy == null) != (verificationReference == null)) {
            throw new IllegalArgumentException("Linking operator and verification reference must be provided together");
        }
        if (linkedBy != null) {
            required(linkedBy, "linkedBy");
            required(verificationReference, "verificationReference");
        }

        var user = users.findById(internalUserId)
                .orElseThrow(() -> new ResourceNotFoundException("Business account not found"));
        var existing = identities.findByProviderAndIssuerAndSubject(
                normalizedProvider, verifiedIssuer, verifiedSubject);
        if (existing.isPresent()) {
            if (existing.get().getUser().getId().equals(internalUserId)) {
                return existing.get();
            }
            throw new IdentityAlreadyLinkedException();
        }

        try {
            return identities.saveAndFlush(new AuthIdentityEntity(
                    user, normalizedProvider, verifiedIssuer, verifiedSubject, linkedBy, verificationReference));
        } catch (DataIntegrityViolationException exception) {
            // The unique key is the final guard against concurrent linking attempts.
            throw new IdentityAlreadyLinkedException();
        }
    }

    private static String required(String value, String name) {
        if (value == null || value.isBlank() || !value.equals(value.trim())) {
            throw new IllegalArgumentException(name + " must be present without surrounding whitespace");
        }
        return value;
    }
}
