package com.healthdocs.backend.domain.model;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record CaseConsent(
        UUID id,
        UUID caseId,
        UUID patientId,
        String termsVersion,
        Instant acceptedAt) {

    public CaseConsent {
        Objects.requireNonNull(id, "id is required");
        Objects.requireNonNull(caseId, "caseId is required");
        Objects.requireNonNull(patientId, "patientId is required");
        if (termsVersion == null || termsVersion.isBlank()) {
            throw new IllegalArgumentException("termsVersion is required");
        }
        termsVersion = termsVersion.trim();
        Objects.requireNonNull(acceptedAt, "acceptedAt is required");
    }
}
