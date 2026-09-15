package com.healthdocs.backend.adapter.out.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
        name = "case_consent",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_case_consent_case_terms",
                columnNames = {"case_id", "terms_version"}))
public class CaseConsentEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "case_id", nullable = false)
    private MedicalCaseEntity medicalCase;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "patient_id", nullable = false)
    private AppUserEntity patient;

    @Column(name = "terms_version", nullable = false, length = 50)
    private String termsVersion;

    @Column(name = "accepted_at", nullable = false)
    private Instant acceptedAt;

    protected CaseConsentEntity() {
    }

    public CaseConsentEntity(
            UUID id,
            MedicalCaseEntity medicalCase,
            AppUserEntity patient,
            String termsVersion,
            Instant acceptedAt) {
        this.id = id;
        this.medicalCase = medicalCase;
        this.patient = patient;
        this.termsVersion = termsVersion;
        this.acceptedAt = acceptedAt;
    }

    public UUID getId() {
        return id;
    }

    public MedicalCaseEntity getMedicalCase() {
        return medicalCase;
    }

    public AppUserEntity getPatient() {
        return patient;
    }

    public String getTermsVersion() {
        return termsVersion;
    }

    public Instant getAcceptedAt() {
        return acceptedAt;
    }
}
