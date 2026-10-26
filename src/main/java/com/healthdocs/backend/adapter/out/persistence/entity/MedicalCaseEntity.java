package com.healthdocs.backend.adapter.out.persistence.entity;

import com.healthdocs.backend.domain.model.CaseStatus;
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
import java.time.LocalDate;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

@Entity
@Table(name = "medical_case")
public class MedicalCaseEntity {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "patient_id", nullable = false)
    private AppUserEntity patient;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private CaseStatus status;

    @Column(name = "reference_code", nullable = false, unique = true, length = 32)
    private String referenceCode;

    @Column(name = "study_name", nullable = false, length = 255)
    private String studyName;

    @Column(name = "additional_reference", length = 100)
    private String additionalReference;

    @Column(name = "origin_institution", length = 200)
    private String originInstitution;

    @Column(name = "study_date")
    private LocalDate studyDate;

    @Column(length = 500)
    private String notes;

    @Column(name = "received_at")
    private Instant receivedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected MedicalCaseEntity() {
    }

    public MedicalCaseEntity(UUID id, AppUserEntity patient, CaseStatus status, String referenceCode, String studyName) {
        this.id = id;
        this.patient = patient;
        this.status = status;
        this.referenceCode = referenceCode;
        this.studyName = studyName;
    }

    public UUID getId() {
        return id;
    }

    public AppUserEntity getPatient() {
        return patient;
    }

    public CaseStatus getStatus() {
        return status;
    }

    public void setStatus(CaseStatus status) {
        if (status == null) {
            throw new IllegalArgumentException("Case status is required");
        }
        if (this.status != null && this.status != status && !this.status.canTransitionTo(status)) {
            throw new IllegalStateException("Invalid case status transition: " + this.status + " -> " + status);
        }
        this.status = status;
    }

    public String getReferenceCode() {
        return referenceCode;
    }

    public String getStudyName() {
        return studyName;
    }

    public String getAdditionalReference() { return additionalReference; }
    public String getOriginInstitution() { return originInstitution; }
    public LocalDate getStudyDate() { return studyDate; }
    public String getNotes() { return notes; }
    public void setCaseDetails(String additionalReference, String originInstitution, LocalDate studyDate, String notes) {
        this.additionalReference = additionalReference;
        this.originInstitution = originInstitution;
        this.studyDate = studyDate;
        this.notes = notes;
    }

    public Instant getReceivedAt() { return receivedAt; }
    public void setReceivedAt(Instant value) { receivedAt = value; }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }

    public void makeResultAvailable(Instant at) {
        if (!status.canTransitionTo(CaseStatus.RESULT_AVAILABLE)) {
            throw new IllegalStateException("Case cannot become result available from " + status);
        }
        this.status = CaseStatus.RESULT_AVAILABLE;
        this.updatedAt = at;
    }
}
