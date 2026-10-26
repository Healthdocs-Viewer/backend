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
import com.healthdocs.backend.domain.model.ResearcherAuthorizationStatus;
import java.time.Clock;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AssignCaseUseCase {
    private final MedicalCaseRepository cases;
    private final StoredFileRepository files;
    private final CaseAssignmentRepository assignments;
    private final AppUserRepository users;
    private final Clock clock;
    private com.healthdocs.backend.application.port.out.ActivityEventPort activityEvents;

    public AssignCaseUseCase(MedicalCaseRepository cases, StoredFileRepository files,
            CaseAssignmentRepository assignments, AppUserRepository users, Clock clock) {
        this.cases = cases;
        this.files = files;
        this.assignments = assignments;
        this.users = users;
        this.clock = clock;
    }

    @Autowired
    public void setActivityEventRecorder(
            com.healthdocs.backend.application.port.out.ActivityEventPort activityEvents) {
        this.activityEvents = activityEvents;
    }

    @Transactional
    public void execute(AuthenticatedUser actor, UUID caseId, UUID targetUserId) {
        if (actor == null || !actor.enabled()
                || (actor.role() != Role.COORDINATOR && actor.role() != Role.RESEARCHER)) {
            throw new ForbiddenOperationException("Only enabled coordinators and researchers can assign cases");
        }
        if (actor.role() == Role.RESEARCHER && !actor.id().equals(targetUserId)) {
            throw new ForbiddenOperationException("Researchers can only assign cases to themselves");
        }
        MedicalCaseEntity medicalCase = cases.findByIdForUpdate(caseId)
                .orElseThrow(() -> new ResourceNotFoundException("Case not found"));
        if (medicalCase.getStatus() == CaseStatus.INPUT_CHANGE_DRAFT
                || medicalCase.getStatus() == CaseStatus.INPUT_CHANGE_REVIEW) {
            throw new BusinessRuleViolationException("Assignments are blocked while an input-change request is open");
        }
        if (medicalCase.getStatus() != CaseStatus.FILES_RECEIVED &&
                medicalCase.getStatus() != CaseStatus.ENABLED_FOR_PROCESSING &&
                medicalCase.getStatus() != CaseStatus.RESULT_UPLOADED &&
                medicalCase.getStatus() != CaseStatus.RESULT_AVAILABLE) {
            throw new BusinessRuleViolationException("Case is not ready for processing");
        }
        if (files.countByMedicalCaseIdAndFileKindAndInputActiveTrue(caseId, FileKind.INPUT) == 0) {
            throw new BusinessRuleViolationException("Case has no input file");
        }
        var target = users.findById(targetUserId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        if (!target.isEnabled() || (target.getRole() != Role.RESEARCHER && target.getRole() != Role.COORDINATOR)) {
            throw new ForbiddenOperationException("Target user is not an enabled internal user");
        }
        if (target.getRole() == Role.RESEARCHER &&
                target.getAuthorizationStatus() != ResearcherAuthorizationStatus.AUTHORIZED &&
                !assignments.existsByCaseIdAndUserId(caseId, targetUserId)) {
            throw new BusinessRuleViolationException("Restricted researchers cannot receive new assignments");
        }
        // El coordinador que habilita el caso también debe conservar acceso operativo
        // para descargar el insumo, cargar el resultado y publicarlo.
        if (actor.role() == Role.COORDINATOR && !assignments.existsByCaseIdAndUserId(caseId, actor.id())) {
            assignments.save(new CaseAssignmentEntity(caseId, actor.id()));
        }
        boolean newlyAssigned = !assignments.existsByCaseIdAndUserId(caseId, targetUserId);
        if (newlyAssigned) {
            assignments.save(new CaseAssignmentEntity(caseId, targetUserId));
        }
        boolean processingEnabled = medicalCase.getStatus() == CaseStatus.FILES_RECEIVED;
        if (processingEnabled) {
            medicalCase.setStatus(CaseStatus.ENABLED_FOR_PROCESSING);
        }
        medicalCase.setUpdatedAt(clock.instant());
        cases.save(medicalCase);
        if (activityEvents != null) {
            var patientId = medicalCase.getPatient().getId();
            if (newlyAssigned && target.getRole() == Role.RESEARCHER) {
                activityEvents.record("RESEARCHER_ASSIGNED", clock.instant(), caseId, actor.id(), patientId,
                        "USER", targetUserId, "RESEARCHER_ASSIGNED:" + caseId + ":" + targetUserId + ":" + UUID.randomUUID());
            }
            if (processingEnabled) {
                activityEvents.record("CASE_PROCESSING_ENABLED", clock.instant(), caseId, actor.id(), patientId,
                        "CASE", caseId, "CASE_PROCESSING_ENABLED:" + caseId + ":" + UUID.randomUUID());
            }
        }
    }
}
