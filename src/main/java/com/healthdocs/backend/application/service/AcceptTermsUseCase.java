package com.healthdocs.backend.application.service;

import com.healthdocs.backend.application.port.out.CaseRepositoryPort;
import com.healthdocs.backend.domain.model.CaseConsent;
import com.healthdocs.backend.domain.model.CaseStatus;
import com.healthdocs.backend.domain.model.MedicalCase;
import com.healthdocs.backend.domain.model.Role;
import java.time.Clock;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AcceptTermsUseCase {

    private static final Set<String> VALID_TERMS_VERSIONS = Set.of("1.0");

    private final CaseRepositoryPort cases;
    private final Clock clock;
    private com.healthdocs.backend.application.port.out.ActivityEventPort activityEvents;

    public AcceptTermsUseCase(CaseRepositoryPort cases, Clock clock) {
        this.cases = cases;
        this.clock = clock;
    }

    @Autowired
    public void setActivityEventRecorder(
            com.healthdocs.backend.application.port.out.ActivityEventPort activityEvents) {
        this.activityEvents = activityEvents;
    }

    @Transactional
    public CaseConsent execute(Command command) {
        requireEnabledCaseActor(command.patientId(), command.role(), command.enabled());
        String termsVersion = requireKnownTermsVersion(command.termsVersion());
        MedicalCase medicalCase = cases.findCaseById(command.caseId())
                .orElseThrow(() -> new ResourceNotFoundException("Case not found"));
        requireOwnership(medicalCase, command.patientId());
        if (medicalCase.status() != CaseStatus.CONSENT_PENDING) {
            throw new BusinessRuleViolationException("Consent can only be accepted while the case is consent pending");
        }
        if (cases.existsConsent(command.caseId(), termsVersion)) {
            throw new BusinessRuleViolationException("Consent already registered for terms version");
        }
        Instant now = clock.instant();
        Instant acceptedAt = command.acceptedAt() == null ? now : command.acceptedAt();
        CaseConsent consent = new CaseConsent(
                UUID.randomUUID(), command.caseId(), command.patientId(), termsVersion, acceptedAt);
        CaseConsent saved = cases.saveConsent(consent);
        medicalCase.consentAccepted(now);
        cases.saveCase(medicalCase);
        if (activityEvents != null) {
            activityEvents.record("CONSENT_ACCEPTED", acceptedAt, command.caseId(), command.patientId(),
                    command.patientId(), "CONSENT", saved.id(), "CONSENT_ACCEPTED:" + saved.id());
        }
        return saved;
    }

    static void requireEnabledCaseActor(UUID userId, Role role, boolean enabled) {
        if (userId == null || !enabled || role == null) {
            throw new ForbiddenOperationException("Only enabled users can operate on owned cases");
        }
    }

    static void requireOwnership(MedicalCase medicalCase, UUID patientId) {
        if (!medicalCase.patientId().equals(patientId)) {
            throw new ForbiddenOperationException("User does not own this case");
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

    public record Command(UUID patientId, Role role, boolean enabled, UUID caseId, String termsVersion, Instant acceptedAt) {
        public Command(UUID patientId, Role role, boolean enabled, UUID caseId, String termsVersion) {
            this(patientId, role, enabled, caseId, termsVersion, null);
        }
    }
}
