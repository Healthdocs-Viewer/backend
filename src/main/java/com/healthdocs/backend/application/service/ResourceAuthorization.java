package com.healthdocs.backend.application.service;

import com.healthdocs.backend.adapter.out.persistence.CaseAssignmentRepository;
import com.healthdocs.backend.adapter.out.persistence.MedicalCaseRepository;
import com.healthdocs.backend.adapter.out.persistence.StoredFileRepository;
import com.healthdocs.backend.application.port.out.AuthProvider.AuthenticatedUser;
import com.healthdocs.backend.domain.model.Role;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class ResourceAuthorization {

    private final MedicalCaseRepository cases;
    private final CaseAssignmentRepository assignments;
    private final StoredFileRepository files;

    public ResourceAuthorization(
            MedicalCaseRepository cases,
            CaseAssignmentRepository assignments,
            StoredFileRepository files) {
        this.cases = cases;
        this.assignments = assignments;
        this.files = files;
    }

    public boolean canAccessCase(AuthenticatedUser user, UUID caseId) {
        if (!user.enabled()) {
            return false;
        }
        if (user.role() == Role.PACIENTE) {
            return cases.existsByIdAndPatientId(caseId, user.id());
        }
        return isInternal(user) && assignments.existsByCaseIdAndUserId(caseId, user.id());
    }

    public boolean canAccessFile(AuthenticatedUser user, UUID caseId, UUID fileId) {
        return canAccessCase(user, caseId)
                && files.existsByIdAndMedicalCaseId(fileId, caseId);
    }

    public boolean canAssignCases(AuthenticatedUser user) {
        return user.enabled() && user.role() == Role.PROFESOR;
    }

    public boolean canPublishResults(AuthenticatedUser user, UUID caseId) {
        return canAccessCase(user, caseId) && user.role() == Role.PROFESOR;
    }

    private boolean isInternal(AuthenticatedUser user) {
        return user.role() == Role.ESTUDIANTE || user.role() == Role.PROFESOR;
    }
}
