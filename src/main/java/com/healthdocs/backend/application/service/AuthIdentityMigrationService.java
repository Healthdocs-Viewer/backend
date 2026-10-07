package com.healthdocs.backend.application.service;

import com.healthdocs.backend.adapter.out.persistence.AppUserRepository;
import com.healthdocs.backend.adapter.out.persistence.AuthIdentityRepository;
import com.healthdocs.backend.adapter.out.persistence.entity.AuthIdentityEntity;
import com.healthdocs.backend.application.port.out.InvalidAuthenticationTokenException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.stereotype.Service;

/** Verifies a live Supabase access token before linking it to an explicitly selected business user. */
@Service
@ConditionalOnProperty(name = "app.auth-provider", havingValue = "supabase")
public class AuthIdentityMigrationService {

    private static final String PROVIDER = "supabase";

    private final AppUserRepository users;
    private final AuthIdentityRepository identities;
    private final AuthIdentityLinkingService linking;
    private final JwtDecoder jwtDecoder;

    public AuthIdentityMigrationService(
            AppUserRepository users,
            AuthIdentityRepository identities,
            AuthIdentityLinkingService linking,
            JwtDecoder jwtDecoder) {
        this.users = users;
        this.identities = identities;
        this.linking = linking;
        this.jwtDecoder = jwtDecoder;
    }

    public MigrationPreview preview(UUID internalUserId, String accessToken) {
        VerifiedIdentity identity = verify(internalUserId, accessToken);
        var linkedIdentity = identities.findByProviderAndIssuerAndSubject(
                PROVIDER, identity.issuer(), identity.subject());
        if (linkedIdentity.isPresent()) {
            if (!linkedIdentity.get().getUser().getId().equals(internalUserId)) {
                throw new IdentityAlreadyLinkedException();
            }
            return new MigrationPreview(internalUserId, LinkStatus.ALREADY_LINKED, fingerprint(identity));
        }
        return new MigrationPreview(internalUserId, LinkStatus.WOULD_LINK, fingerprint(identity));
    }

    public AuthIdentityEntity link(
            UUID internalUserId,
            String accessToken,
            String linkedBy,
            String verificationReference) {
        VerifiedIdentity identity = verify(internalUserId, accessToken);
        return linking.linkVerifiedIdentity(
                internalUserId,
                PROVIDER,
                identity.issuer(),
                identity.subject(),
                linkedBy,
                verificationReference);
    }

    private VerifiedIdentity verify(UUID internalUserId, String accessToken) {
        if (internalUserId == null) {
            throw new IllegalArgumentException("Internal user ID is required");
        }
        if (accessToken == null || accessToken.isBlank()) {
            throw new IllegalArgumentException("A Supabase access token is required");
        }
        users.findById(internalUserId)
                .orElseThrow(() -> new ResourceNotFoundException("Business account not found"));

        final Jwt jwt;
        try {
            jwt = jwtDecoder.decode(accessToken);
        } catch (JwtException | IllegalArgumentException exception) {
            // Never include the bearer token or decoder exception in the error/log message.
            throw new InvalidAuthenticationTokenException();
        }
        String issuer = jwt.getIssuer() == null ? null : jwt.getIssuer().toString();
        String subject = jwt.getSubject();
        if (issuer == null || issuer.isBlank() || subject == null || subject.isBlank()) {
            throw new InvalidAuthenticationTokenException();
        }
        return new VerifiedIdentity(issuer, subject);
    }

    private static String fingerprint(VerifiedIdentity identity) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest((identity.issuer() + "\n" + identity.subject()).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    public record MigrationPreview(UUID internalUserId, LinkStatus status, String identityFingerprint) {
    }

    public enum LinkStatus {
        WOULD_LINK,
        ALREADY_LINKED
    }

    private record VerifiedIdentity(String issuer, String subject) {
    }
}
