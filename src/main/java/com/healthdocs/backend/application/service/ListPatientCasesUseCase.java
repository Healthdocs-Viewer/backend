package com.healthdocs.backend.application.service;

import com.healthdocs.backend.application.port.out.CaseRepositoryPort;
import com.healthdocs.backend.application.port.out.AuthProvider.AuthenticatedUser;
import com.healthdocs.backend.domain.model.MedicalCase;
import com.healthdocs.backend.domain.model.Role;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class ListPatientCasesUseCase {

    private final CaseRepositoryPort cases;

    public ListPatientCasesUseCase(CaseRepositoryPort cases) {
        this.cases = cases;
    }

    public List<CaseView> execute(AuthenticatedUser user) {
        if (user.role() != Role.PATIENT || !user.enabled()) {
            throw new ForbiddenOperationException("Only enabled patients can list patient cases");
        }
        return cases.findAllByPatientId(user.id()).stream().map(CaseView::from).toList();
    }

    public record CaseView(UUID id, String referenceCode, String studyName, com.healthdocs.backend.domain.model.CaseStatus status, Instant createdAt) {
        static CaseView from(MedicalCase medicalCase) {
            return new CaseView(medicalCase.id(), medicalCase.referenceCode(), medicalCase.studyName(), medicalCase.status(), medicalCase.createdAt());
        }
    }
}
