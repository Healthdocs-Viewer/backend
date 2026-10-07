package com.healthdocs.backend.application.service;

import com.healthdocs.backend.adapter.out.persistence.AppUserRepository;
import com.healthdocs.backend.adapter.out.persistence.CaseAssignmentRepository;
import com.healthdocs.backend.adapter.out.persistence.MedicalCaseRepository;
import com.healthdocs.backend.adapter.out.persistence.StoredFileRepository;
import com.healthdocs.backend.adapter.out.persistence.entity.CaseAssignmentEntity;
import com.healthdocs.backend.adapter.out.persistence.entity.MedicalCaseEntity;
import com.healthdocs.backend.application.port.out.AuthProvider.AuthenticatedUser;
import com.healthdocs.backend.domain.model.CaseStatus;
import com.healthdocs.backend.domain.model.FileKind;
import com.healthdocs.backend.domain.model.Role;
import java.time.Clock;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AssignCaseUseCase {
    private final MedicalCaseRepository cases;
    private final StoredFileRepository files;
    private final CaseAssignmentRepository assignments;
    private final AppUserRepository users;
    private final Clock clock;

    public AssignCaseUseCase(MedicalCaseRepository cases, StoredFileRepository files,
            CaseAssignmentRepository assignments, AppUserRepository users, Clock clock) {
        this.cases = cases;
        this.files = files;
        this.assignments = assignments;
        this.users = users;
        this.clock = clock;
    }

    @Transactional
    public void execute(AuthenticatedUser coordinator, UUID caseId, UUID targetUserId) {
        if (coordinator == null || !coordinator.enabled() || coordinator.role() != Role.COORDINATOR) {
            throw new ForbiddenOperationException("Only enabled coordinators can assign cases");
        }
        MedicalCaseEntity medicalCase = cases.findById(caseId)
                .orElseThrow(() -> new ResourceNotFoundException("Case not found"));
        if (medicalCase.getStatus() != CaseStatus.FILES_RECEIVED) {
            throw new BusinessRuleViolationException("Case is not ready for processing");
        }
        if (files.countByMedicalCaseIdAndFileKind(caseId, FileKind.INPUT) == 0) {
            throw new BusinessRuleViolationException("Case has no input file");
        }
        var target = users.findById(targetUserId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        if (!target.isEnabled() || (target.getRole() != Role.RESEARCHER && target.getRole() != Role.COORDINATOR)) {
            throw new ForbiddenOperationException("Target user is not an enabled internal user");
        }
        // El coordinador que habilita el caso también debe conservar acceso operativo
        // para descargar el insumo, cargar el resultado y publicarlo.
        if (!assignments.existsByCaseIdAndUserId(caseId, coordinator.id())) {
            assignments.save(new CaseAssignmentEntity(caseId, coordinator.id()));
        }
        if (!assignments.existsByCaseIdAndUserId(caseId, targetUserId)) {
            assignments.save(new CaseAssignmentEntity(caseId, targetUserId));
        }
        medicalCase.setStatus(CaseStatus.ENABLED_FOR_PROCESSING);
        medicalCase.setUpdatedAt(clock.instant());
        cases.save(medicalCase);
    }
}
