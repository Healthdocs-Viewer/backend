package com.healthdocs.backend.application.service;

import com.healthdocs.backend.application.port.out.CaseRepositoryPort;
import com.healthdocs.backend.domain.model.CaseConsent;
import com.healthdocs.backend.domain.model.MedicalCase;
import com.healthdocs.backend.domain.model.Role;
import java.time.Clock;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AcceptTermsUseCase {

    private static final Set<String> VALID_TERMS_VERSIONS = Set.of("1.0");

    private final CaseRepositoryPort cases;
    private final Clock clock;

    public AcceptTermsUseCase(CaseRepositoryPort cases, Clock clock) {
        this.cases = cases;
        this.clock = clock;
    }

    @Transactional
    public CaseConsent execute(Command command) {
        requirePatient(command.patientId(), command.role(), command.enabled());
        String termsVersion = requireKnownTermsVersion(command.termsVersion());
        MedicalCase medicalCase = cases.findCaseById(command.caseId())
                .orElseThrow(() -> new ResourceNotFoundException("Case not found"));
        requireOwnership(medicalCase, command.patientId());
        if (cases.existsConsent(command.caseId(), termsVersion)) {
            throw new BusinessRuleViolationException("Consent already registered for terms version");
        }
        CaseConsent consent = new CaseConsent(
                UUID.randomUUID(), command.caseId(), command.patientId(), termsVersion, clock.instant());
        return cases.saveConsent(consent);
    }

    static void requirePatient(UUID patientId, Role role, boolean enabled) {
        if (patientId == null || !enabled || role != Role.PATIENT) {
            throw new ForbiddenOperationException("Only enabled patients can operate on patient cases");
        }
    }

    static void requireOwnership(MedicalCase medicalCase, UUID patientId) {
        if (!medicalCase.patientId().equals(patientId)) {
            throw new ForbiddenOperationException("Patient does not own this case");
        }
    }

    private static String requireKnownTermsVersion(String termsVersion) {
        if (termsVersion == null || termsVersion.isBlank()) {
            throw new IllegalArgumentException("termsVersion is required");
        }
        String normalized = termsVersion.trim();
        if (!VALID_TERMS_VERSIONS.contains(normalized)) {
            throw new IllegalArgumentException("Unknown terms version");
        }
        return normalized;
    }

    public record Command(UUID patientId, Role role, boolean enabled, UUID caseId, String termsVersion) {
    }
}
