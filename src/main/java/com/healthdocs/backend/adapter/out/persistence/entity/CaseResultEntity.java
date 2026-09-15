package com.healthdocs.backend.adapter.out.persistence.entity;

import com.healthdocs.backend.domain.model.ResultStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
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

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "case_id", nullable = false, unique = true)
    private MedicalCaseEntity medicalCase;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "file_id", nullable = false)
    private StoredFileEntity file;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private ResultStatus status;

    @Column(name = "available_at")
    private Instant availableAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

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

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
