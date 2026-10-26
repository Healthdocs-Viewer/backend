package com.healthdocs.backend.adapter.out.persistence;

import com.healthdocs.backend.adapter.out.persistence.entity.CaseConsentEntity;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CaseConsentRepository extends JpaRepository<CaseConsentEntity, UUID> {

    @org.springframework.data.jpa.repository.Query("select distinct c.medicalCase.id from CaseConsentEntity c where c.patient.id = :patientId")
    java.util.Set<UUID> findConsentedCaseIdsByPatientId(@org.springframework.data.repository.query.Param("patientId") UUID patientId);

    boolean existsByMedicalCaseIdAndTermsVersion(UUID caseId, String termsVersion);

    boolean existsByMedicalCaseId(UUID caseId);
}
