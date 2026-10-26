package com.healthdocs.backend.adapter.out.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "input_change_file_cleanup")
public class InputChangeFileCleanupEntity {
    @Id
    @Column(name = "file_id")
    private UUID fileId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "request_id", nullable = false)
    private InputChangeRequestEntity request;

    @Column(name = "eligible_at", nullable = false)
    private Instant eligibleAt;

    @Column(name = "requested_at", nullable = false)
    private Instant requestedAt;

    @Column(name = "processed_at")
    private Instant processedAt;

    @Column(nullable = false)
    private int attempts;

    @Column(name = "last_error_code", length = 60)
    private String lastErrorCode;

    protected InputChangeFileCleanupEntity() { }

    public InputChangeFileCleanupEntity(StoredFileEntity file, InputChangeRequestEntity request, Instant now) {
        this.fileId = file.getId();
        this.request = request;
        this.requestedAt = now;
        this.eligibleAt = file.getUploadExpiresAt() != null && file.getUploadExpiresAt().isAfter(now)
                ? file.getUploadExpiresAt() : now;
    }

    public UUID getFileId() { return fileId; }
    public Instant getEligibleAt() { return eligibleAt; }
    public Instant getProcessedAt() { return processedAt; }
    public int getAttempts() { return attempts; }

    public void markSkipped(Instant at) {
        processedAt = at;
        lastErrorCode = null;
    }

    public void markSucceeded(Instant at) {
        attempts++;
        processedAt = at;
        lastErrorCode = null;
    }

    public void recordFailedAttempt(String errorCode, Instant nextEligibleAt) {
        attempts++;
        eligibleAt = nextEligibleAt;
        lastErrorCode = errorCode;
    }
}
