package com.healthdocs.backend.application.port.out;

/** Verifies a provider access token and returns only its trusted identity claims. */
public interface ExternalIdentityTokenVerifier {

    VerifiedExternalIdentity verify(String accessToken);

    record VerifiedExternalIdentity(String provider, String issuer, String subject, String email) {
    }
}
