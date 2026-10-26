package com.healthdocs.backend.adapter.out.persistence.entity;

import com.healthdocs.backend.domain.model.ResultStatus;
import com.healthdocs.backend.domain.model.ResultPublicationType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

@Entity
@Table(name = "case_result")
public class CaseResultEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "case_id", nullable = false)
    private MedicalCaseEntity medicalCase;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "file_id", nullable = false)
    private StoredFileEntity file;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private ResultStatus status;

    @Column(name = "available_at")
    private Instant availableAt;

    @Column(name = "available_by")
    private UUID availableBy;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "uploaded_by")
    private UUID uploadedBy;
    @Column(name = "uploaded_at")
    private Instant uploadedAt;

    @Column(name = "version_number")
    private Integer versionNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "publication_type", length = 30)
    private ResultPublicationType publicationType;

    @Column(name = "is_current", nullable = false)
    private boolean current;

    @Column(name = "correction_request", columnDefinition = "text")
    private String correctionRequest;

    @Column(name = "correction_reason", columnDefinition = "text")
    private String correctionReason;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @Column(name = "deleted_by")
    private UUID deletedBy;

    @Column(name = "deletion_reason", columnDefinition = "text")
    private String deletionReason;

    protected CaseResultEntity() {
    }

    public CaseResultEntity(
            MedicalCaseEntity medicalCase,
            StoredFileEntity file,
            ResultStatus status) {
        this.medicalCase = medicalCase;
        this.file = file;
        this.status = status;
    }

    public UUID getId() {
        return id;
    }

    public MedicalCaseEntity getMedicalCase() {
        return medicalCase;
    }

    public StoredFileEntity getFile() {
        return file;
    }

    public ResultStatus getStatus() {
        return status;
    }

    public Instant getAvailableAt() {
        return availableAt;
    }

    public void makeAvailable(Instant at, UUID userId) {
        this.status = ResultStatus.AVAILABLE;
        this.availableAt = at;
        this.availableBy = userId;
    }

    public void publishAsVersion(int number, ResultPublicationType type, Instant at, UUID userId) {
        this.versionNumber = number;
        this.publicationType = type;
        this.current = true;
        this.correctionRequest = null;
        makeAvailable(at, userId);
    }

    public void reserveVersion(int number, ResultPublicationType type) {
        if (number < 1 || type == null) throw new IllegalArgumentException("Valid version metadata is required");
        this.versionNumber = number;
        this.publicationType = type;
        this.current = true;
    }

    public void requestCorrection(String reason) {
        this.status = ResultStatus.CORRECTION_REQUIRED;
        this.correctionRequest = reason;
        this.correctionReason = reason;
    }

    public void resolveCorrectionAsHistory() {
        this.status = ResultStatus.AVAILABLE;
        this.correctionRequest = null;
        this.current = false;
    }

    public void markNotCurrent() {
        this.current = false;
        this.correctionRequest = null;
    }

    public void softDelete(Instant at, UUID userId, String reason) {
        this.status = ResultStatus.DELETED;
        this.current = false;
        this.correctionRequest = null;
        this.deletedAt = at;
        this.deletedBy = userId;
        this.deletionReason = reason;
    }

    public UUID getAvailableBy() { return availableBy; }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void recordUpload(UUID userId, Instant at) { this.uploadedBy = userId; this.uploadedAt = at; }
    public UUID getUploadedBy() { return uploadedBy; }
    public Instant getUploadedAt() { return uploadedAt; }
    public Integer getVersionNumber() { return versionNumber; }
    public ResultPublicationType getPublicationType() { return publicationType; }
    public boolean isCurrent() { return current; }
    public String getCorrectionRequest() { return correctionRequest; }
    public String getCorrectionReason() { return correctionReason; }
    public Instant getDeletedAt() { return deletedAt; }
    public UUID getDeletedBy() { return deletedBy; }
    public String getDeletionReason() { return deletionReason; }
    public boolean isDeleted() { return deletedAt != null; }
}
