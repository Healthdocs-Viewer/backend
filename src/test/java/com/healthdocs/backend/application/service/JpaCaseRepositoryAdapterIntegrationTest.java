package com.healthdocs.backend.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.healthdocs.backend.adapter.out.persistence.AppUserRepository;
import com.healthdocs.backend.adapter.out.persistence.CaseConsentRepository;
import com.healthdocs.backend.adapter.out.persistence.JpaCaseRepositoryAdapter;
import com.healthdocs.backend.adapter.out.persistence.MedicalCaseRepository;
import com.healthdocs.backend.adapter.out.persistence.entity.AppUserEntity;
import com.healthdocs.backend.domain.model.CaseStatus;
import com.healthdocs.backend.domain.model.CaseConsent;
import com.healthdocs.backend.domain.model.MedicalCase;
import com.healthdocs.backend.domain.model.Role;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

/** Verifies the explicit persist path used for domain-assigned case identifiers. */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class JpaCaseRepositoryAdapterIntegrationTest {

    @Autowired
    private JpaCaseRepositoryAdapter caseRepository;

    @Autowired
    private AppUserRepository users;

    @Autowired
    private MedicalCaseRepository cases;

    @Autowired
    private CaseConsentRepository consents;

    @Test
    void persistsANewCaseWithADomainAssignedUuid() {
        AppUserEntity patient = users.save(new AppUserEntity(
                "patient-" + UUID.randomUUID(),
                "patient-" + UUID.randomUUID() + "@healthdocs.test",
                Role.PACIENTE));
        UUID caseId = UUID.randomUUID();
        Instant now = Instant.parse("2026-09-16T00:00:00Z");

        MedicalCase saved = caseRepository.saveCase(new MedicalCase(
                caseId,
                patient.getId(),
                "CAS-20260916-0001",
                "RADIOGRAFIA_TORAX",
                CaseStatus.CREATED,
                now,
                now));

        assertEquals(caseId, saved.id());
        assertNotNull(saved.createdAt());
        assertTrue(cases.existsById(caseId));
        assertEquals("RADIOGRAFIA_TORAX", cases.findById(caseId).orElseThrow().getStudyType());

        UUID consentId = UUID.randomUUID();
        CaseConsent savedConsent = caseRepository.saveConsent(new CaseConsent(
                consentId, caseId, patient.getId(), "1.0", now));
        assertEquals(consentId, savedConsent.id());
        assertTrue(consents.existsById(consentId));
    }
}
