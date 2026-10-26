package com.healthdocs.backend.domain.model;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

public class MedicalCase {

    private final UUID id;
    private final UUID patientId;
    private final String referenceCode;
    private final String studyName;
    private final String additionalReference;
    private final String originInstitution;
    private final LocalDate studyDate;
    private final String notes;
    private CaseStatus status;
    private final Instant createdAt;
    private Instant updatedAt;
    private Instant receivedAt;

    public MedicalCase(
            UUID id,
            UUID patientId,
            String referenceCode,
            String studyName,
            CaseStatus status,
            Instant createdAt,
            Instant updatedAt) {
        this(id, patientId, referenceCode, studyName, status, createdAt, updatedAt,
                null);
    }

    public MedicalCase(UUID id, UUID patientId, String referenceCode, String studyName, CaseStatus status,
            Instant createdAt, Instant updatedAt, Instant receivedAt) {
        this(id, patientId, referenceCode, studyName, null, null, null, null, status, createdAt, updatedAt, receivedAt);
    }

    public MedicalCase(UUID id, UUID patientId, String referenceCode, String studyName, String additionalReference,
            String originInstitution, LocalDate studyDate, String notes, CaseStatus status,
            Instant createdAt, Instant updatedAt, Instant receivedAt) {
        this.id = Objects.requireNonNull(id, "id is required");
        this.patientId = Objects.requireNonNull(patientId, "patientId is required");
        this.referenceCode = requireText(referenceCode, "referenceCode");
        this.studyName = requireText(studyName, "studyName");
        this.additionalReference = optionalText(additionalReference);
        this.originInstitution = optionalText(originInstitution);
        this.studyDate = studyDate;
        this.notes = optionalText(notes);
        this.status = Objects.requireNonNull(status, "status is required");
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt is required");
        this.updatedAt = Objects.requireNonNull(updatedAt, "updatedAt is required");
        this.receivedAt = receivedAt;
    }

    public static MedicalCase create(UUID id, UUID patientId, String referenceCode, String studyName,
            String additionalReference, String originInstitution, LocalDate studyDate, String notes, Instant now) {
        return new MedicalCase(id, patientId, referenceCode, studyName, additionalReference, originInstitution,
                studyDate, notes, CaseStatus.CONSENT_PENDING, now, now, null);
    }

    public void transitionTo(CaseStatus next, Instant now) {
        Objects.requireNonNull(next, "next status is required");
        if (!status.canTransitionTo(next)) {
            throw new IllegalStateException("Invalid case status transition: " + status + " -> " + next);
        }
        this.status = next;
        this.updatedAt = Objects.requireNonNull(now, "now is required");
    }

    public void consentAccepted(Instant now) {
        transitionTo(CaseStatus.FILE_PENDING, now);
    }

    public void fileUploaded(Instant now) {
        if (status == CaseStatus.FILE_PENDING) transitionTo(CaseStatus.CONFIRMATION_PENDING, now);
        else if (status != CaseStatus.CONFIRMATION_PENDING) throw new IllegalStateException("Case is not accepting input files");
    }

    public void confirmSubmission(Instant now) {
        if (status != CaseStatus.CONFIRMATION_PENDING) throw new IllegalStateException("Case is not ready for confirmation");
        transitionTo(CaseStatus.FILES_RECEIVED, now);
        receivedAt = now;
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

    public String studyName() {
        return studyName;
    }
    public String additionalReference() { return additionalReference; }
    public String originInstitution() { return originInstitution; }
    public LocalDate studyDate() { return studyDate; }
    public String notes() { return notes; }

    public CaseStatus status() {
        return status;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant updatedAt() {
        return updatedAt;
    }

    public Instant receivedAt() { return receivedAt; }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
        return value.trim();
    }

    private static String optionalText(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
