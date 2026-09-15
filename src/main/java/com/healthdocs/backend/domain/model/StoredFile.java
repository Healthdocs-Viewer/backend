package com.healthdocs.backend.domain.model;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public class StoredFile {

    private final UUID id;
    private final UUID caseId;
    private final String objectKey;
    private final FileKind fileKind;
    private final String originalFilename;
    private final String contentType;
    private final Long sizeBytes;
    private final Instant createdAt;
    private Instant updatedAt;

    public StoredFile(
            UUID id,
            UUID caseId,
            String objectKey,
            FileKind fileKind,
            String originalFilename,
            String contentType,
            Long sizeBytes,
            Instant createdAt,
            Instant updatedAt) {
        this.id = Objects.requireNonNull(id, "id is required");
        this.caseId = Objects.requireNonNull(caseId, "caseId is required");
        this.objectKey = requireText(objectKey, "objectKey");
        this.fileKind = Objects.requireNonNull(fileKind, "fileKind is required");
        this.originalFilename = requireText(originalFilename, "originalFilename");
        this.contentType = requireText(contentType, "contentType");
        if (sizeBytes != null && sizeBytes < 1) {
            throw new IllegalArgumentException("sizeBytes must be positive");
        }
        this.sizeBytes = sizeBytes;
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt is required");
        this.updatedAt = Objects.requireNonNull(updatedAt, "updatedAt is required");
    }

    public void markConfirmed(Instant now) {
        this.updatedAt = Objects.requireNonNull(now, "now is required");
    }

    public UUID id() {
        return id;
    }

    public UUID caseId() {
        return caseId;
    }

    public String objectKey() {
        return objectKey;
    }

    public FileKind fileKind() {
        return fileKind;
    }

    public String originalFilename() {
        return originalFilename;
    }

    public String contentType() {
        return contentType;
    }

    public Long sizeBytes() {
        return sizeBytes;
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
