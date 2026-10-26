package com.healthdocs.backend.application.service;

import com.healthdocs.backend.adapter.out.persistence.AppUserRepository;
import com.healthdocs.backend.adapter.out.persistence.CaseResultRepository;
import com.healthdocs.backend.adapter.out.persistence.InputChangeFileCleanupRepository;
import com.healthdocs.backend.adapter.out.persistence.InputChangeRequestFileRepository;
import com.healthdocs.backend.adapter.out.persistence.InputChangeRequestRepository;
import com.healthdocs.backend.adapter.out.persistence.MedicalCaseRepository;
import com.healthdocs.backend.adapter.out.persistence.StoredFileRepository;
import com.healthdocs.backend.adapter.out.persistence.entity.AppUserEntity;
import com.healthdocs.backend.adapter.out.persistence.entity.InputChangeFileCleanupEntity;
import com.healthdocs.backend.adapter.out.persistence.entity.InputChangeRequestEntity;
import com.healthdocs.backend.adapter.out.persistence.entity.InputChangeRequestFileEntity;
import com.healthdocs.backend.adapter.out.persistence.entity.MedicalCaseEntity;
import com.healthdocs.backend.adapter.out.persistence.entity.StoredFileEntity;
import com.healthdocs.backend.application.port.out.AuthProvider.AuthenticatedUser;
import com.healthdocs.backend.application.port.out.StoragePort;
import com.healthdocs.backend.domain.model.CaseStatus;
import com.healthdocs.backend.domain.model.FileKind;
import com.healthdocs.backend.domain.model.InputChangeFileSet;
import com.healthdocs.backend.domain.model.InputChangeRequestStatus;
import com.healthdocs.backend.domain.model.Role;
import com.healthdocs.backend.infrastructure.config.InternalFlowProperties;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

@Service
public class InputChangeReviewService {
    private final InputChangeRequestRepository requests;
    private final InputChangeRequestFileRepository requestFiles;
    private final MedicalCaseRepository cases;
    private final StoredFileRepository files;
    private final AppUserRepository users;
    private final CaseResultRepository results;
    private final InputChangeFileCleanupRepository cleanup;
    private final InputChangeDraftService draftViews;
    private final StoragePort storage;
    private final InternalFlowProperties properties;
    private final Clock clock;
    private com.healthdocs.backend.application.port.out.SecurityAuditEventPort securityAudit;
    private com.healthdocs.backend.application.port.out.ActivityEventPort activityEvents;

    public InputChangeReviewService(InputChangeRequestRepository requests,
            InputChangeRequestFileRepository requestFiles, MedicalCaseRepository cases,
            StoredFileRepository files, AppUserRepository users, CaseResultRepository results,
            InputChangeFileCleanupRepository cleanup, InputChangeDraftService draftViews,
            StoragePort storage, InternalFlowProperties properties, Clock clock) {
        this.requests = requests;
        this.requestFiles = requestFiles;
        this.cases = cases;
        this.files = files;
        this.users = users;
        this.results = results;
        this.cleanup = cleanup;
        this.draftViews = draftViews;
        this.storage = storage;
        this.properties = properties;
        this.clock = clock;
    }

    @Autowired
    public void setSecurityAuditEventPort(
            com.healthdocs.backend.application.port.out.SecurityAuditEventPort securityAudit) {
        this.securityAudit = securityAudit;
    }

    @Autowired
    public void setActivityEventRecorder(
            com.healthdocs.backend.application.port.out.ActivityEventPort activityEvents) {
        this.activityEvents = activityEvents;
    }

    @Transactional(readOnly = true)
    public List<RequestSummary> listPending(AuthenticatedUser coordinator) {
        requireCoordinator(coordinator);
        return requests.findAllByStatusOrderBySubmittedAtAsc(InputChangeRequestStatus.PENDING_REVIEW).stream()
                .filter(request -> !request.getRequestedBy().getId().equals(coordinator.id()))
                .filter(request -> !results.existsByMedicalCaseId(request.getMedicalCase().getId()))
                .map(request -> new RequestSummary(request.getId(), request.getMedicalCase().getId(),
                        request.getRequestedBy().getId(), request.getMedicalCase().getReferenceCode(),
                        request.getMedicalCase().getStudyName(), request.getReason(),
                        request.proposedFiles().size(), request.getCreatedAt(), request.getSubmittedAt()))
                .toList();
    }

    @Transactional(readOnly = true)
    public InputChangeDraftService.RequestView getForReview(AuthenticatedUser coordinator, UUID requestId) {
        requireCoordinator(coordinator);
        InputChangeRequestEntity request = pendingRequest(requestId);
        requireNotOwner(coordinator, request);
        if (results.existsByMedicalCaseId(request.getMedicalCase().getId())) {
            throw new BusinessRuleViolationException("A case with a result cannot have an input-change request reviewed");
        }
        return draftViews.view(request);
    }

    @Transactional
    public DownloadView createDownload(AuthenticatedUser coordinator, UUID requestId, UUID fileId) {
        requireCoordinator(coordinator);
        InputChangeRequestEntity request = pendingRequest(requestId);
        requireNotOwner(coordinator, request);
        if (results.existsByMedicalCaseId(request.getMedicalCase().getId())) {
            throw new BusinessRuleViolationException("A case with a result cannot have an input-change request reviewed");
        }
        boolean belongsToRequest = requestFiles.findFirstByRequest_IdAndFile_IdAndFileSetAndRemovedAtIsNull(
                        requestId, fileId, InputChangeFileSet.ORIGINAL).isPresent()
                || requestFiles.findFirstByRequest_IdAndFile_IdAndFileSetAndRemovedAtIsNull(
                        requestId, fileId, InputChangeFileSet.PROPOSED).isPresent();
        if (!belongsToRequest) throw new ResourceNotFoundException("Review file not found");
        StoredFileEntity file = files.findById(fileId)
                .orElseThrow(() -> new ResourceNotFoundException("Review file not found"));
        if (file.getFileKind() != FileKind.INPUT
                || !file.getMedicalCase().getId().equals(request.getMedicalCase().getId())
                || !storage.objectExists(file.getObjectKey())) {
            throw new ResourceNotFoundException("Review file not found");
        }
        StoragePort.UploadUrlResult url = storage.generateDownloadUrl(file.getObjectKey(), file.getContentType(),
                properties.downloadTtlMinutes());
        if (securityAudit != null) {
            securityAudit.record("SENSITIVE_RESOURCE_ACCESS_GRANTED", clock.instant(), coordinator.id(),
                    coordinator.id(), request.getMedicalCase().getId(), "INPUT_FILE", fileId,
                    "DOWNLOAD_URL_ISSUED", null, null, null,
                    "SENSITIVE_RESOURCE_ACCESS_GRANTED:" + UUID.randomUUID());
        }
        return new DownloadView(url.uploadUrl(), url.expiresAt());
    }

    @Transactional
    public void approve(AuthenticatedUser coordinator, UUID requestId) {
        DecisionContext context = lockPendingRequest(coordinator, requestId);
        InputChangeRequestEntity request = context.request();
        MedicalCaseEntity medicalCase = context.medicalCase();
        if (results.existsByMedicalCaseId(medicalCase.getId())) {
            throw new BusinessRuleViolationException("A case with a result cannot have its inputs changed");
        }

        List<InputChangeRequestFileEntity> proposed = activeFiles(requestId, InputChangeFileSet.PROPOSED);
        if (proposed.isEmpty()) {
            throw new BusinessRuleViolationException("The proposal has no INPUT files");
        }
        Instant now = clock.instant();
        for (InputChangeRequestFileEntity membership : proposed) {
            StoredFileEntity file = lockFile(membership.getFile().getId());
            if (file.getFileKind() != FileKind.INPUT || !file.getMedicalCase().getId().equals(medicalCase.getId())
                    || file.getConfirmedAt() == null || !storage.objectExists(file.getObjectKey())) {
                throw new BusinessRuleViolationException("The proposal contains an incomplete or unavailable INPUT file");
            }
        }

        List<StoredFileEntity> currentFiles = files.findByMedicalCaseIdAndFileKindAndInputActiveTrue(
                medicalCase.getId(), FileKind.INPUT);
        for (StoredFileEntity current : currentFiles) {
            StoredFileEntity lockedCurrent = lockFile(current.getId());
            if (proposed.stream().noneMatch(membership -> membership.getFile().getId().equals(lockedCurrent.getId()))) {
                lockedCurrent.stageInput();
            }
        }
        for (InputChangeRequestFileEntity membership : proposed) {
            lockFile(membership.getFile().getId()).activateInput();
        }

        AppUserEntity decisionMaker = coordinatorEntity(coordinator);
        request.decide(true, decisionMaker, null, now);
        medicalCase.setStatus(request.getResumeCaseStatus());
        medicalCase.setUpdatedAt(now);
        requests.saveAndFlush(request);
        files.flush();
        cases.saveAndFlush(medicalCase);
        if (activityEvents != null) activityEvents.record("INPUT_CHANGE_APPROVED", now, medicalCase.getId(),
                coordinator.id(), request.getRequestedBy().getId(), "INPUT_CHANGE_REQUEST", requestId,
                "INPUT_CHANGE_APPROVED:" + requestId);
    }

    @Transactional
    public void reject(AuthenticatedUser coordinator, UUID requestId, String reason) {
        if (reason == null || reason.isBlank() || reason.trim().length() > 1000) {
            throw new IllegalArgumentException("Rejection reason must contain 1 to 1000 characters");
        }
        DecisionContext context = lockPendingRequest(coordinator, requestId);
        InputChangeRequestEntity request = context.request();
        MedicalCaseEntity medicalCase = context.medicalCase();
        if (results.existsByMedicalCaseId(medicalCase.getId())) {
            throw new BusinessRuleViolationException("A case with a result cannot have an input-change request reviewed");
        }
        Instant now = clock.instant();
        request.decide(false, coordinatorEntity(coordinator), reason, now);
        for (InputChangeRequestFileEntity membership : activeFiles(requestId, InputChangeFileSet.PROPOSED)) {
            StoredFileEntity file = membership.getFile();
            if (!file.isInputActive()) scheduleCleanup(request, file, now);
        }
        medicalCase.setStatus(request.getResumeCaseStatus());
        medicalCase.setUpdatedAt(now);
        requests.saveAndFlush(request);
        cases.saveAndFlush(medicalCase);
        if (activityEvents != null) activityEvents.record("INPUT_CHANGE_REJECTED", now, medicalCase.getId(),
                coordinator.id(), request.getRequestedBy().getId(), "INPUT_CHANGE_REQUEST", requestId,
                "INPUT_CHANGE_REJECTED:" + requestId);
    }

    private DecisionContext lockPendingRequest(AuthenticatedUser coordinator, UUID requestId) {
        requireCoordinator(coordinator);
        UUID caseId = requests.findCaseIdById(requestId)
                .orElseThrow(() -> new ResourceNotFoundException("Input-change request not found"));
        MedicalCaseEntity medicalCase = cases.findByIdForUpdate(caseId)
                .orElseThrow(() -> new ResourceNotFoundException("Case not found"));
        InputChangeRequestEntity request = requests.findByIdForUpdate(requestId)
                .orElseThrow(() -> new ResourceNotFoundException("Input-change request not found"));
        requireNotOwner(coordinator, request);
        if (request.getStatus() != InputChangeRequestStatus.PENDING_REVIEW
                || medicalCase.getStatus() != CaseStatus.INPUT_CHANGE_REVIEW) {
            throw new BusinessRuleViolationException("Input-change request is no longer pending review");
        }
        return new DecisionContext(request, medicalCase);
    }

    private InputChangeRequestEntity pendingRequest(UUID requestId) {
        InputChangeRequestEntity request = requests.findById(requestId)
                .orElseThrow(() -> new ResourceNotFoundException("Input-change request not found"));
        if (request.getStatus() != InputChangeRequestStatus.PENDING_REVIEW
                || request.getMedicalCase().getStatus() != CaseStatus.INPUT_CHANGE_REVIEW) {
            throw new ResourceNotFoundException("Pending input-change request not found");
        }
        return request;
    }

    private List<InputChangeRequestFileEntity> activeFiles(UUID requestId, InputChangeFileSet fileSet) {
        return requestFiles.findAllByRequest_IdAndFileSetAndRemovedAtIsNullOrderByAddedAtAsc(requestId, fileSet);
    }

    private StoredFileEntity lockFile(UUID fileId) {
        return files.findByIdForUpdate(fileId)
                .orElseThrow(() -> new ResourceNotFoundException("Input file not found"));
    }

    private void scheduleCleanup(InputChangeRequestEntity request, StoredFileEntity file, Instant now) {
        if (!file.isInputActive() && !cleanup.existsById(file.getId())) {
            cleanup.save(new InputChangeFileCleanupEntity(file, request, now));
        }
    }

    private AppUserEntity coordinatorEntity(AuthenticatedUser coordinator) {
        return users.findById(coordinator.id())
                .orElseThrow(() -> new ResourceNotFoundException("Coordinator not found"));
    }

    private static void requireCoordinator(AuthenticatedUser coordinator) {
        if (coordinator == null || !coordinator.enabled() || coordinator.role() != Role.COORDINATOR) {
            throw new ForbiddenOperationException("Only enabled coordinators can review input-change requests");
        }
    }

    private static void requireNotOwner(AuthenticatedUser coordinator, InputChangeRequestEntity request) {
        if (coordinator.id().equals(request.getRequestedBy().getId())) {
            throw new ForbiddenOperationException("A coordinator cannot review their own request");
        }
    }

    private record DecisionContext(InputChangeRequestEntity request, MedicalCaseEntity medicalCase) { }

    public record DownloadView(String downloadUrl, Instant expiresAt) { }

    public record RequestSummary(UUID id, UUID caseId, UUID requestedBy, String caseReferenceCode,
            String studyName, String reason, int fileCount, Instant createdAt, Instant submittedAt) { }
}
