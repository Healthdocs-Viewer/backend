package com.healthdocs.backend.adapter.out.persistence.entity;

import com.healthdocs.backend.domain.model.FileKind;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

@Entity
@Table(name = "stored_file")
public class StoredFileEntity {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "case_id", nullable = false)
    private MedicalCaseEntity medicalCase;

    @Column(name = "object_key", nullable = false, unique = true, length = 1024)
    private String objectKey;

    @Enumerated(EnumType.STRING)
    @Column(name = "file_kind", nullable = false, length = 50)
    private FileKind fileKind;

    @Column(name = "original_filename", nullable = false, length = 1024)
    private String originalFilename;

    @Column(name = "content_type", length = 255)
    private String contentType;

    @Column(name = "size_bytes")
    private Long sizeBytes;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "confirmed_at")
    private Instant confirmedAt;

    @Column(name = "upload_initiated_by")
    private UUID uploadInitiatedBy;

    @Column(name = "upload_expires_at")
    private Instant uploadExpiresAt;

    @Column(name = "input_active", nullable = false)
    private boolean inputActive = true;

    protected StoredFileEntity() {
    }

    public StoredFileEntity(
            UUID id,
            MedicalCaseEntity medicalCase,
            String objectKey,
            FileKind fileKind,
            String originalFilename,
            String contentType,
            Long sizeBytes) {
        this(id, medicalCase, objectKey, fileKind, originalFilename, contentType, sizeBytes, null);
    }

    public StoredFileEntity(
            UUID id,
            MedicalCaseEntity medicalCase,
            String objectKey,
            FileKind fileKind,
            String originalFilename,
            String contentType,
            Long sizeBytes,
            UUID uploadInitiatedBy) {
        this.id = id;
        this.medicalCase = medicalCase;
        this.objectKey = objectKey;
        this.fileKind = fileKind;
        this.originalFilename = originalFilename;
        this.contentType = contentType;
        this.sizeBytes = sizeBytes;
        this.uploadInitiatedBy = uploadInitiatedBy;
    }

    public UUID getId() {
        return id;
    }

    public MedicalCaseEntity getMedicalCase() {
        return medicalCase;
    }

    public String getObjectKey() {
        return objectKey;
    }

    public FileKind getFileKind() {
        return fileKind;
    }

    public String getOriginalFilename() {
        return originalFilename;
    }

    public String getContentType() {
        return contentType;
    }

    public Long getSizeBytes() {
        return sizeBytes;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public Instant getConfirmedAt() { return confirmedAt; }
    public void confirm(Instant at) { if (confirmedAt == null) confirmedAt = at; }
    public UUID getUploadInitiatedBy() { return uploadInitiatedBy; }
    public void setUploadInitiatedBy(UUID userId) { uploadInitiatedBy = userId; }
    public Instant getUploadExpiresAt() { return uploadExpiresAt; }
    public void setUploadExpiresAt(Instant expiresAt) { uploadExpiresAt = expiresAt; }
    public boolean isInputActive() { return inputActive; }
    public void stageInput() { inputActive = false; }
    public void activateInput() { inputActive = true; }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
