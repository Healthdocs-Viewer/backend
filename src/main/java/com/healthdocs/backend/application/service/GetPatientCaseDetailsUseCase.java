package com.healthdocs.backend.application.service;

import com.healthdocs.backend.application.port.out.AuthProvider.AuthenticatedUser;
import com.healthdocs.backend.application.port.out.CaseRepositoryPort;
import com.healthdocs.backend.domain.model.MedicalCase;
import com.healthdocs.backend.domain.model.CaseStatus;
import com.healthdocs.backend.domain.model.Role;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class GetPatientCaseDetailsUseCase {
    private static final List<CaseStatus> INTERNAL_CASE_STATUSES = List.of(
            CaseStatus.ENABLED_FOR_PROCESSING, CaseStatus.INPUT_CHANGE_DRAFT,
            CaseStatus.INPUT_CHANGE_REVIEW, CaseStatus.RESULT_UPLOADED, CaseStatus.RESULT_AVAILABLE);
    private final CaseRepositoryPort cases;

    public GetPatientCaseDetailsUseCase(CaseRepositoryPort cases) { this.cases = cases; }

    public CaseDetails execute(AuthenticatedUser user, UUID caseId) {
        if (user == null || !user.enabled() || user.role() == null)
            throw new ForbiddenOperationException("Only enabled users can view case details");
        MedicalCase medicalCase = cases.findCaseById(caseId)
                .orElseThrow(() -> new ResourceNotFoundException("Case not found"));
        boolean owner = medicalCase.patientId().equals(user.id());
        boolean assignedInternalCase = (user.role() == Role.RESEARCHER || user.role() == Role.COORDINATOR)
                && cases.findAllAssignedInternalCases(user.id(), INTERNAL_CASE_STATUSES).stream()
                        .anyMatch(visible -> visible.id().equals(caseId));
        if (!owner && !assignedInternalCase) throw new ResourceNotFoundException("Case not found");
        return new CaseDetails(medicalCase.id(), medicalCase.referenceCode(), medicalCase.studyName(),
                medicalCase.additionalReference(), medicalCase.originInstitution(), medicalCase.studyDate(),
                medicalCase.notes(), medicalCase.status(),
                medicalCase.createdAt(), medicalCase.updatedAt(), cases.findAvailableResultId(caseId).orElse(null));
    }

    public record CaseDetails(UUID id, String referenceCode, String studyName, String additionalReference,
            String originInstitution, LocalDate studyDate, String notes,
            com.healthdocs.backend.domain.model.CaseStatus status,
            Instant createdAt, Instant updatedAt, UUID availableResultId) { }
}
