package com.healthdocs.backend.adapter.out.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;

@Entity
@Table(name = "case_creation_idempotency", uniqueConstraints = {
        @UniqueConstraint(name = "uq_case_creation_idempotency_patient_key", columnNames = {"patient_id", "idempotency_key"})
})
public class CaseCreationIdempotencyEntity {
    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "patient_id", nullable = false)
    private AppUserEntity patient;

    @Column(name = "idempotency_key", nullable = false)
    private UUID idempotencyKey;

    @Column(name = "request_hash", nullable = false, length = 64)
    private String requestHash;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "case_id", nullable = false, unique = true)
    private MedicalCaseEntity medicalCase;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected CaseCreationIdempotencyEntity() { }

    public CaseCreationIdempotencyEntity(UUID id, AppUserEntity patient, UUID idempotencyKey,
            String requestHash, MedicalCaseEntity medicalCase) {
        this.id = id;
        this.patient = patient;
        this.idempotencyKey = idempotencyKey;
        this.requestHash = requestHash;
        this.medicalCase = medicalCase;
    }

    public String getRequestHash() { return requestHash; }
    public UUID getCaseId() { return medicalCase.getId(); }
}
