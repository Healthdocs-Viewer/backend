package com.healthdocs.backend.domain.model;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public class MedicalCase {

    private final UUID id;
    private final UUID patientId;
    private final String referenceCode;
    private final String studyType;
    private CaseStatus status;
    private final Instant createdAt;
    private Instant updatedAt;

    public MedicalCase(
            UUID id,
            UUID patientId,
            String referenceCode,
            String studyType,
            CaseStatus status,
            Instant createdAt,
            Instant updatedAt) {
        this.id = Objects.requireNonNull(id, "id is required");
        this.patientId = Objects.requireNonNull(patientId, "patientId is required");
        this.referenceCode = requireText(referenceCode, "referenceCode");
        this.studyType = requireText(studyType, "studyType");
        this.status = Objects.requireNonNull(status, "status is required");
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt is required");
        this.updatedAt = Objects.requireNonNull(updatedAt, "updatedAt is required");
    }

    public static MedicalCase create(UUID id, UUID patientId, String referenceCode, String studyType, Instant now) {
        return new MedicalCase(id, patientId, referenceCode, studyType, CaseStatus.CREATED, now, now);
    }

    public void transitionTo(CaseStatus next, Instant now) {
        Objects.requireNonNull(next, "next status is required");
        if (!status.canTransitionTo(next)) {
            throw new IllegalStateException("Invalid case status transition: " + status + " -> " + next);
        }
        this.status = next;
        this.updatedAt = Objects.requireNonNull(now, "now is required");
    }

    public UUID id() {
        return id;
    }

    public UUID patientId() {
        return patientId;
    }

    public String referenceCode() {
        return referenceCode;
    }

    public String studyType() {
        return studyType;
    }

    public CaseStatus status() {
        return status;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant updatedAt() {
        return updatedAt;
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
        return value.trim();
    }
}
