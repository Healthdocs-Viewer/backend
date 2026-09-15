package com.healthdocs.backend.adapter.out.persistence.entity;

import com.healthdocs.backend.domain.model.FileKind;
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
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;

@Entity
@Table(name = "stored_file")
public class StoredFileEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
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

    protected StoredFileEntity() {
    }

    public StoredFileEntity(
            MedicalCaseEntity medicalCase,
            String objectKey,
            FileKind fileKind,
            String originalFilename,
            String contentType,
            Long sizeBytes) {
        this.medicalCase = medicalCase;
        this.objectKey = objectKey;
        this.fileKind = fileKind;
        this.originalFilename = originalFilename;
        this.contentType = contentType;
        this.sizeBytes = sizeBytes;
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
}
