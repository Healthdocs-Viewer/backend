package com.healthdocs.backend.application.port.out;

import com.healthdocs.backend.domain.model.CaseConsent;
import com.healthdocs.backend.domain.model.FileKind;
import com.healthdocs.backend.domain.model.MedicalCase;
import com.healthdocs.backend.domain.model.StoredFile;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

public interface CaseRepositoryPort {

    MedicalCase saveCase(MedicalCase medicalCase);

    Optional<MedicalCase> findCaseById(UUID caseId);

    boolean existsCaseByIdAndPatientId(UUID caseId, UUID patientId);

    long countCasesCreatedOn(LocalDate date);

    CaseConsent saveConsent(CaseConsent consent);

    boolean existsConsent(UUID caseId, String termsVersion);

    boolean hasAnyConsent(UUID caseId);

    StoredFile saveFile(StoredFile file);

    Optional<StoredFile> findFileById(UUID fileId);

    long countFilesByCaseIdAndKind(UUID caseId, FileKind fileKind);
}
