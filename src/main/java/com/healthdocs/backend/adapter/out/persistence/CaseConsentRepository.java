package com.healthdocs.backend.adapter.out.persistence;

import com.healthdocs.backend.adapter.out.persistence.entity.CaseConsentEntity;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CaseConsentRepository extends JpaRepository<CaseConsentEntity, UUID> {

    boolean existsByMedicalCaseIdAndTermsVersion(UUID caseId, String termsVersion);

    boolean existsByMedicalCaseId(UUID caseId);
}
