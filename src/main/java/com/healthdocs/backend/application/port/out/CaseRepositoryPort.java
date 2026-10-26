package com.healthdocs.backend.application.port.out;

import com.healthdocs.backend.domain.model.CaseConsent;
import com.healthdocs.backend.domain.model.CaseStatus;
import com.healthdocs.backend.domain.model.FileKind;
import com.healthdocs.backend.domain.model.MedicalCase;
import com.healthdocs.backend.domain.model.StoredFile;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import java.util.List;

public interface CaseRepositoryPort {

    MedicalCase saveCase(MedicalCase medicalCase);

    Optional<MedicalCase> findCaseById(UUID caseId);

    default Optional<MedicalCase> findCaseByIdForUpdate(UUID caseId) { return findCaseById(caseId); }

    List<MedicalCase> findAllByPatientId(UUID patientId);

    List<MedicalCase> findAllAssignedInternalCases(UUID userId, List<CaseStatus> statuses);

    default Optional<UUID> findAvailableResultId(UUID caseId) { return Optional.empty(); }

    boolean existsCaseByIdAndPatientId(UUID caseId, UUID patientId);

    long countCasesCreatedOn(LocalDate date);

    default void lockCaseCreation(UUID patientId) { }

    default Optional<CaseCreationRequest> findCaseCreationRequest(UUID patientId, UUID idempotencyKey) {
        return Optional.empty();
    }

    default void saveCaseCreationRequest(UUID patientId, UUID idempotencyKey, String requestHash, UUID caseId) { }

    default long nextCaseReferenceNumber(LocalDate date) { return countCasesCreatedOn(date) + 1; }

    record CaseCreationRequest(String requestHash, UUID caseId) { }

    CaseConsent saveConsent(CaseConsent consent);

    boolean existsConsent(UUID caseId, String termsVersion);

    boolean hasAnyConsent(UUID caseId);

    StoredFile saveFile(StoredFile file);

    Optional<StoredFile> findFileById(UUID fileId);

    default Optional<StoredFile> findActiveInputUploadIntent(UUID caseId, java.time.Instant now) {
        return Optional.empty();
    }

    default Optional<StoredFile> findFileByIdForUpdate(UUID fileId) { return findFileById(fileId); }

    long countFilesByCaseIdAndKind(UUID caseId, FileKind fileKind);
}
