package com.healthdocs.backend.application.service;

import com.healthdocs.backend.application.port.out.CaseRepositoryPort;
import com.healthdocs.backend.application.port.out.StoragePort;
import com.healthdocs.backend.domain.model.CaseStatus;
import com.healthdocs.backend.domain.model.FileKind;
import com.healthdocs.backend.domain.model.MedicalCase;
import com.healthdocs.backend.domain.model.Role;
import com.healthdocs.backend.domain.model.StoredFile;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ConfirmFileUploadUseCase {

    private final CaseRepositoryPort cases;
    private final StoragePort storage;
    private final Clock clock;

    public ConfirmFileUploadUseCase(CaseRepositoryPort cases, StoragePort storage, Clock clock) {
        this.cases = cases;
        this.storage = storage;
        this.clock = clock;
    }

    @Transactional
    public StoredFile execute(Command command) {
        AcceptTermsUseCase.requirePatient(command.patientId(), command.role(), command.enabled());
        StoredFile file = cases.findFileById(command.fileId())
                .orElseThrow(() -> new ResourceNotFoundException("File not found"));
        MedicalCase medicalCase = cases.findCaseById(file.caseId())
                .orElseThrow(() -> new ResourceNotFoundException("Case not found"));
        AcceptTermsUseCase.requireOwnership(medicalCase, command.patientId());
        if (file.fileKind() != FileKind.INPUT) {
            throw new BusinessRuleViolationException("Only input files can be confirmed by patients");
        }
        if (!storage.objectExists(file.objectKey())) {
            throw new BusinessRuleViolationException("Uploaded object does not exist");
        }

        Instant now = clock.instant();
        long existingInputFiles = cases.countFilesByCaseIdAndKind(medicalCase.id(), FileKind.INPUT);
        if (medicalCase.status() == CaseStatus.CREATED && existingInputFiles <= 1) {
            medicalCase.transitionTo(CaseStatus.FILES_RECEIVED, now);
            cases.saveCase(medicalCase);
        }
        file.markConfirmed(now);
        return cases.saveFile(file);
    }

    public record Command(UUID patientId, Role role, boolean enabled, UUID fileId) {
    }
}
