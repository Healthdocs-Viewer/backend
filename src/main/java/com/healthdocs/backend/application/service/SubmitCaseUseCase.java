package com.healthdocs.backend.application.service;

import com.healthdocs.backend.application.port.out.CaseRepositoryPort;
import com.healthdocs.backend.domain.model.CaseStatus;
import com.healthdocs.backend.domain.model.FileKind;
import com.healthdocs.backend.domain.model.MedicalCase;
import com.healthdocs.backend.domain.model.Role;
import java.time.Clock;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SubmitCaseUseCase {

    private final CaseRepositoryPort cases;
    private final Clock clock;

    public SubmitCaseUseCase(CaseRepositoryPort cases, Clock clock) {
        this.cases = cases;
        this.clock = clock;
    }

    @Transactional
    public void execute(Command command) {
        AcceptTermsUseCase.requireEnabledCaseActor(command.userId(), command.role(), command.enabled());
        MedicalCase medicalCase = cases.findCaseByIdForUpdate(command.caseId())
                .orElseThrow(() -> new ResourceNotFoundException("Case not found"));
        AcceptTermsUseCase.requireOwnership(medicalCase, command.userId());
        if (medicalCase.status() != CaseStatus.CONFIRMATION_PENDING) {
            throw new BusinessRuleViolationException("Case is not ready for submission");
        }
        if (!cases.hasAnyConsent(command.caseId())) {
            throw new BusinessRuleViolationException("Consent is required before submitting the case");
        }
        if (cases.countFilesByCaseIdAndKind(command.caseId(), FileKind.INPUT) < 1) {
            throw new BusinessRuleViolationException("At least one completed input file is required");
        }

        medicalCase.confirmSubmission(clock.instant());
        cases.saveCase(medicalCase);
    }

    public record Command(UUID userId, Role role, boolean enabled, UUID caseId) { }
}
