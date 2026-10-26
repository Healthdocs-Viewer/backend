package com.healthdocs.backend.application.service;

import com.healthdocs.backend.application.port.out.CaseRepositoryPort;
import com.healthdocs.backend.application.port.out.StoragePort;
import com.healthdocs.backend.adapter.out.persistence.InputChangeRequestFileRepository;
import com.healthdocs.backend.domain.model.CaseStatus;
import com.healthdocs.backend.domain.model.FileKind;
import com.healthdocs.backend.domain.model.InputChangeFileSet;
import com.healthdocs.backend.domain.model.InputChangeRequestStatus;
import com.healthdocs.backend.domain.model.MedicalCase;
import com.healthdocs.backend.domain.model.Role;
import com.healthdocs.backend.domain.model.StoredFile;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ConfirmFileUploadUseCase {

    private final CaseRepositoryPort cases;
    private final StoragePort storage;
    private final Clock clock;
    private final InputChangeRequestFileRepository requestFiles;
    private com.healthdocs.backend.application.port.out.ActivityEventPort activityEvents;

    public ConfirmFileUploadUseCase(CaseRepositoryPort cases, StoragePort storage, Clock clock) {
        this(cases, storage, clock, null);
    }

    @Autowired
    public ConfirmFileUploadUseCase(CaseRepositoryPort cases, StoragePort storage, Clock clock,
            InputChangeRequestFileRepository requestFiles) {
        this.cases = cases;
        this.storage = storage;
        this.clock = clock;
        this.requestFiles = requestFiles;
    }

    @Autowired
    public void setActivityEventRecorder(
            com.healthdocs.backend.application.port.out.ActivityEventPort activityEvents) {
        this.activityEvents = activityEvents;
    }

    @Transactional
    public StoredFile execute(Command command) { return executeWithStatus(command).file(); }

    @Transactional
    public Confirmation executeWithStatus(Command command) {
        AcceptTermsUseCase.requireEnabledCaseActor(command.patientId(), command.role(), command.enabled());
        StoredFile file = cases.findFileByIdForUpdate(command.fileId())
                .orElseThrow(() -> new ResourceNotFoundException("File not found"));
        MedicalCase medicalCase = cases.findCaseByIdForUpdate(file.caseId())
                .orElseThrow(() -> new ResourceNotFoundException("Case not found"));
        AcceptTermsUseCase.requireOwnership(medicalCase, command.patientId());
        if (file.fileKind() != FileKind.INPUT) {
            throw new BusinessRuleViolationException("Only INPUT files can be confirmed through the case-owner flow");
        }
        if (file.uploadInitiatedBy() == null || !file.uploadInitiatedBy().equals(command.patientId())) {
            throw new ForbiddenOperationException("Only the case owner who initiated the upload can complete it");
        }
        if (!file.inputActive()) {
            var membership = requestFiles == null ? java.util.Optional
                    .<com.healthdocs.backend.adapter.out.persistence.entity.InputChangeRequestFileEntity>empty()
                    : requestFiles.findFirstByFile_IdAndFileSetAndRemovedAtIsNullOrderByAddedAtDesc(
                            file.id(), InputChangeFileSet.PROPOSED);
            if (membership.isEmpty()
                    || !membership.get().getRequest().getMedicalCase().getId().equals(medicalCase.id())
                    || membership.get().getRequest().getStatus() != InputChangeRequestStatus.DRAFT
                    || medicalCase.status() != CaseStatus.INPUT_CHANGE_DRAFT) {
                throw new BusinessRuleViolationException("Proposed upload is no longer editable");
            }
            Instant now = clock.instant();
            if (file.confirmed()) return new Confirmation(file, medicalCase.status());
            if (file.uploadExpiresAt() == null || !now.isBefore(file.uploadExpiresAt())) {
                throw new BusinessRuleViolationException("Upload intent has expired");
            }
            if (!storage.objectExists(file.objectKey())) {
                throw new BusinessRuleViolationException("Uploaded object does not exist");
            }
            file.markConfirmed(now);
            StoredFile saved = cases.saveFile(file);
            if (activityEvents != null) {
                var request = membership.get().getRequest();
                activityEvents.record("INPUT_CHANGE_FILE_ADDED", now, medicalCase.id(), command.patientId(),
                        command.patientId(), "INPUT_CHANGE_REQUEST", request.getId(),
                        "INPUT_CHANGE_FILE_ADDED:" + request.getId() + ":" + file.id());
            }
            return new Confirmation(saved, medicalCase.status());
        }
        Instant now = clock.instant();
        if (file.confirmed()) return new Confirmation(file, medicalCase.status());
        if (file.uploadExpiresAt() == null || !now.isBefore(file.uploadExpiresAt())) {
            throw new BusinessRuleViolationException("Upload intent has expired");
        }
        if (!storage.objectExists(file.objectKey())) {
            throw new BusinessRuleViolationException("Uploaded object does not exist");
        }
        if (medicalCase.status() != CaseStatus.FILE_PENDING
                && medicalCase.status() != CaseStatus.CONFIRMATION_PENDING) {
            throw new BusinessRuleViolationException("Case is not accepting input files");
        }
        if (!cases.hasAnyConsent(medicalCase.id())) {
            throw new BusinessRuleViolationException("Consent is required before confirming upload");
        }
        medicalCase.fileUploaded(now);
        cases.saveCase(medicalCase);
        file.markConfirmed(now);
        StoredFile saved = cases.saveFile(file);
        if (activityEvents != null) {
            activityEvents.record("FILE_RECEIVED", now, medicalCase.id(), command.patientId(),
                    command.patientId(), "FILE", file.id(), "FILE_RECEIVED:" + file.id());
        }
        return new Confirmation(saved, medicalCase.status());
    }

    public record Command(UUID patientId, Role role, boolean enabled, UUID fileId) {
    }

    public record Confirmation(StoredFile file, CaseStatus caseStatus) { }
}
