package com.healthdocs.backend.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.healthdocs.backend.adapter.out.persistence.AppUserRepository;
import com.healthdocs.backend.adapter.out.persistence.AuthIdentityRepository;
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
    private AuthIdentityRepository identities;

    @Autowired
    private MedicalCaseRepository cases;

    @Autowired
    private CaseConsentRepository consents;

    @Autowired
    private AuthIdentityLinkingService identityLinking;

    @Test
    void persistsANewCaseWithADomainAssignedUuid() {
        AppUserEntity patient = users.save(new AppUserEntity(
                "patient-" + UUID.randomUUID(),
                "patient-" + UUID.randomUUID() + "@healthdocs.test",
                Role.PATIENT));
        UUID caseId = UUID.randomUUID();
        Instant now = Instant.parse("2026-09-16T00:00:00Z");

        MedicalCase saved = caseRepository.saveCase(new MedicalCase(
                caseId,
                patient.getId(),
                "CAS-20260916-0001",
                "Radiografía de tórax",
                CaseStatus.CREATED,
                now,
                now));

        assertEquals(caseId, saved.id());
        assertNotNull(saved.createdAt());
        assertTrue(cases.existsById(caseId));
        assertEquals("Radiografía de tórax", cases.findById(caseId).orElseThrow().getStudyName());

        UUID consentId = UUID.randomUUID();
        CaseConsent savedConsent = caseRepository.saveConsent(new CaseConsent(
                consentId, caseId, patient.getId(), "1.0", now));
        assertEquals(consentId, savedConsent.id());
        assertTrue(consents.existsById(consentId));
    }

    @Test
    void linksVerifiedIdentityWithoutChangingBusinessUserOrCaseOwnership() {
        AppUserEntity patient = users.save(new AppUserEntity(
                "legacy-subject-" + UUID.randomUUID(),
                "patient-" + UUID.randomUUID() + "@healthdocs.test",
                Role.PATIENT));
        UUID caseId = UUID.randomUUID();
        Instant now = Instant.parse("2026-09-16T00:00:00Z");
        caseRepository.saveCase(new MedicalCase(
                caseId, patient.getId(), "CAS-20260916-0002", "MRI", CaseStatus.CREATED, now, now));

        identityLinking.linkVerifiedIdentity(patient.getId(), "supabase", "https://issuer.test", "new-subject");

        assertEquals(patient.getId(), users.findByAuthIdentity(
                "supabase", "https://issuer.test", "new-subject").orElseThrow().getId());
        assertEquals(patient.getId(), caseRepository.findCaseById(caseId).orElseThrow().patientId());
    }

    @Test
    void rejectsAnIdentityAlreadyLinkedToAnotherBusinessAccountAndLeavesUnknownIdentityUnmapped() {
        AppUserEntity first = users.save(new AppUserEntity(
                "legacy-first-" + UUID.randomUUID(), "first@healthdocs.test", Role.PATIENT));
        AppUserEntity second = users.save(new AppUserEntity(
                "legacy-second-" + UUID.randomUUID(), "second@healthdocs.test", Role.PATIENT));
        identityLinking.linkVerifiedIdentity(first.getId(), "supabase", "https://issuer.test", "shared-subject");

        assertThrows(IdentityAlreadyLinkedException.class, () -> identityLinking.linkVerifiedIdentity(
                second.getId(), "supabase", "https://issuer.test", "shared-subject"));
        assertTrue(users.findByAuthIdentity("supabase", "https://issuer.test", "unknown-subject").isEmpty());
        assertEquals(first.getId(), users.findByAuthIdentity(
                "supabase", "https://issuer.test", "shared-subject").orElseThrow().getId());
    }

    @Test
    void linkingTheSameIdentityToTheSameAccountIsIdempotent() {
        AppUserEntity patient = users.save(new AppUserEntity(
                "legacy-idempotent-" + UUID.randomUUID(), "idempotent@healthdocs.test", Role.PATIENT));

        identityLinking.linkVerifiedIdentity(patient.getId(), "supabase", "https://issuer.test", "stable-subject");
        identityLinking.linkVerifiedIdentity(patient.getId(), "supabase", "https://issuer.test", "stable-subject");

        assertEquals(1, identities.findByUser_Id(patient.getId()).stream()
                .filter(identity -> identity.getProvider().equals("supabase"))
                .count());
    }
}
