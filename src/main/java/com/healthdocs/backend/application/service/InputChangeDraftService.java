package com.healthdocs.backend.application.service;

import com.healthdocs.backend.adapter.out.persistence.AppUserRepository;
import com.healthdocs.backend.adapter.out.persistence.CaseResultRepository;
import com.healthdocs.backend.adapter.out.persistence.InputChangeRequestFileRepository;
import com.healthdocs.backend.adapter.out.persistence.InputChangeFileCleanupRepository;
import com.healthdocs.backend.adapter.out.persistence.InputChangeRequestRepository;
import com.healthdocs.backend.adapter.out.persistence.MedicalCaseRepository;
import com.healthdocs.backend.adapter.out.persistence.StoredFileRepository;
import com.healthdocs.backend.adapter.out.persistence.entity.AppUserEntity;
import com.healthdocs.backend.adapter.out.persistence.entity.InputChangeRequestEntity;
import com.healthdocs.backend.adapter.out.persistence.entity.InputChangeFileCleanupEntity;
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
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

@Service
public class InputChangeDraftService {
    private static final int UPLOAD_TTL_MINUTES = 15;
    private static final List<InputChangeRequestStatus> ACTIVE_STATUSES =
            List.of(InputChangeRequestStatus.DRAFT, InputChangeRequestStatus.PENDING_REVIEW);

    private final MedicalCaseRepository cases;
    private final StoredFileRepository files;
    private final AppUserRepository users;
    private final CaseResultRepository results;
    private final InputChangeRequestRepository requests;
    private final InputChangeRequestFileRepository requestFiles;
    private final InputChangeFileCleanupRepository cleanup;
    private final StoragePort storage;
    private final Clock clock;
    private com.healthdocs.backend.application.port.out.ActivityEventPort activityEvents;

    public InputChangeDraftService(MedicalCaseRepository cases, StoredFileRepository files,
            AppUserRepository users, CaseResultRepository results,
            InputChangeRequestRepository requests, InputChangeRequestFileRepository requestFiles,
            InputChangeFileCleanupRepository cleanup, StoragePort storage, Clock clock) {
        this.cases = cases;
        this.files = files;
        this.users = users;
        this.results = results;
        this.requests = requests;
        this.requestFiles = requestFiles;
        this.cleanup = cleanup;
        this.storage = storage;
        this.clock = clock;
    }

    @Autowired
    public void setActivityEventRecorder(
            com.healthdocs.backend.application.port.out.ActivityEventPort activityEvents) {
        this.activityEvents = activityEvents;
    }

    @Transactional
    public RequestView open(AuthenticatedUser actor, UUID caseId) {
        MedicalCaseEntity medicalCase = ownedCase(actor, caseId, true);
        CaseStatus previousStatus = medicalCase.getStatus();
        if (previousStatus != CaseStatus.FILES_RECEIVED
                && previousStatus != CaseStatus.ENABLED_FOR_PROCESSING) {
            throw new BusinessRuleViolationException("Case is not accepting input-change requests");
        }
        if (results.existsByMedicalCaseId(caseId)) {
            throw new BusinessRuleViolationException("A case with a result cannot request an input change");
        }
        if (requests.existsByMedicalCase_IdAndStatusIn(caseId, ACTIVE_STATUSES)) {
            throw new BusinessRuleViolationException("An input-change request is already active");
        }
        List<StoredFileEntity> originals = files.findByMedicalCaseIdAndFileKindAndInputActiveTrue(
                caseId, FileKind.INPUT);
        if (originals.isEmpty()) throw new BusinessRuleViolationException("Case has no current INPUT files");
        AppUserEntity owner = users.findById(actor.id())
                .orElseThrow(() -> new ResourceNotFoundException("Owner not found"));
        Instant now = clock.instant();
        InputChangeRequestEntity request = new InputChangeRequestEntity(medicalCase, owner, previousStatus);
        request.initializeFileSets(originals, actor.id(), now);
        request = requests.saveAndFlush(request);
        medicalCase.setStatus(CaseStatus.INPUT_CHANGE_DRAFT);
        medicalCase.setUpdatedAt(now);
        cases.saveAndFlush(medicalCase);
        if (activityEvents != null) activityEvents.record("INPUT_CHANGE_OPENED", now, caseId, actor.id(), actor.id(),
                "INPUT_CHANGE_REQUEST", request.getId(), "INPUT_CHANGE_OPENED:" + request.getId());
        return view(request);
    }

    @Transactional(readOnly = true)
    public List<RequestView> list(AuthenticatedUser actor, UUID caseId) {
        ownedCase(actor, caseId, false);
        return requests.findAllByMedicalCase_IdAndRequestedBy_IdOrderByCreatedAtDesc(caseId, actor.id()).stream()
                .map(this::view).toList();
    }

    @Transactional(readOnly = true)
    public RequestView get(AuthenticatedUser actor, UUID caseId, UUID requestId) {
        ownedCase(actor, caseId, false);
        return view(ownedRequest(actor, caseId, requestId));
    }

    @Transactional
    public RequestView updateReason(AuthenticatedUser actor, UUID caseId, UUID requestId, String reason) {
        MedicalCaseEntity medicalCase = ownedCase(actor, caseId, true);
        InputChangeRequestEntity request = editableRequest(actor, medicalCase, requestId);
        if (reason == null || reason.isBlank() || reason.trim().length() > 250) {
            throw new IllegalArgumentException("Reason must contain 1 to 250 characters");
        }
        request.updateReason(reason);
        requests.saveAndFlush(request);
        return view(request);
    }

    @Transactional
    public UploadIntent createUpload(AuthenticatedUser actor, UUID caseId, UUID requestId,
            String originalFilename, String contentType, Long sizeBytes) {
        MedicalCaseEntity medicalCase = ownedCase(actor, caseId, true);
        InputChangeRequestEntity request = editableRequest(actor, medicalCase, requestId);
        if (originalFilename == null || originalFilename.isBlank() || originalFilename.length() > 1024
                || originalFilename.contains("..") || originalFilename.contains("/")
                || originalFilename.contains("\\")) {
            throw new IllegalArgumentException("Invalid originalFilename");
        }
        if (contentType == null || contentType.isBlank() || contentType.length() > 255) {
            throw new IllegalArgumentException("Invalid contentType");
        }
        if (sizeBytes == null || sizeBytes < 1) {
            throw new IllegalArgumentException("sizeBytes must be positive");
        }
        Instant now = clock.instant();
        UUID fileId = UUID.randomUUID();
        String objectKey = "input-change/%s/%s/%s/%s".formatted(
                caseId, requestId, fileId, originalFilename.trim());
        StoragePort.UploadUrlResult uploadUrl = storage.generateUploadUrl(
                objectKey, contentType.trim(), UPLOAD_TTL_MINUTES);
        if (uploadUrl.expiresAt() == null || !now.isBefore(uploadUrl.expiresAt())) {
            throw new IllegalStateException("Storage provider returned an invalid upload URL expiration");
        }
        StoredFileEntity file = new StoredFileEntity(fileId, medicalCase, objectKey, FileKind.INPUT,
                originalFilename.trim(), contentType.trim(), sizeBytes, actor.id());
        file.stageInput();
        file.setUploadExpiresAt(uploadUrl.expiresAt());
        files.saveAndFlush(file);
        InputChangeRequestFileEntity membership = request.addFile(
                file, InputChangeFileSet.PROPOSED, actor.id(), now);
        requestFiles.saveAndFlush(membership);
        return new UploadIntent(fileView(file), uploadUrl.uploadUrl(), uploadUrl.expiresAt());
    }

    @Transactional
    public void remove(AuthenticatedUser actor, UUID caseId, UUID requestId, UUID fileId) {
        MedicalCaseEntity medicalCase = ownedCase(actor, caseId, true);
        InputChangeRequestEntity request = editableRequest(actor, medicalCase, requestId);
        InputChangeRequestFileEntity membership = requestFiles
                .findFirstByRequest_IdAndFile_IdAndFileSetAndRemovedAtIsNull(
                        requestId, fileId, InputChangeFileSet.PROPOSED)
                .orElseThrow(() -> new ResourceNotFoundException("Proposed file not found"));
        request.removeProposedFile(fileId, actor.id(), clock.instant());
        requestFiles.saveAndFlush(membership);
        scheduleCleanup(request, membership.getFile(), clock.instant());
        if (activityEvents != null) activityEvents.record("INPUT_CHANGE_FILE_REMOVED", membership.getRemovedAt(),
                caseId, actor.id(), actor.id(), "INPUT_CHANGE_REQUEST", requestId,
                "INPUT_CHANGE_FILE_REMOVED:" + requestId + ":" + fileId + ":" + membership.getId());
    }

    @Transactional
    public void cancel(AuthenticatedUser actor, UUID caseId, UUID requestId) {
        MedicalCaseEntity medicalCase = ownedCase(actor, caseId, true);
        InputChangeRequestEntity request = editableRequest(actor, medicalCase, requestId);
        Instant now = clock.instant();
        AppUserEntity owner = users.findById(actor.id())
                .orElseThrow(() -> new ResourceNotFoundException("Owner not found"));
        request.cancel(owner, now);
        requests.saveAndFlush(request);
        request.getFiles().stream()
                .filter(membership -> membership.getFileSet() == InputChangeFileSet.PROPOSED)
                .forEach(membership -> scheduleCleanup(request, membership.getFile(), now));
        medicalCase.setStatus(request.getResumeCaseStatus());
        medicalCase.setUpdatedAt(now);
        cases.saveAndFlush(medicalCase);
        if (activityEvents != null) activityEvents.record("INPUT_CHANGE_CANCELLED", now, caseId, actor.id(), actor.id(),
                "INPUT_CHANGE_REQUEST", requestId, "INPUT_CHANGE_CANCELLED:" + requestId);
    }

    @Transactional
    public void submit(AuthenticatedUser actor, UUID caseId, UUID requestId) {
        MedicalCaseEntity medicalCase = ownedCase(actor, caseId, true);
        InputChangeRequestEntity request = editableRequest(actor, medicalCase, requestId);
        if (medicalCase.getStatus() != CaseStatus.INPUT_CHANGE_DRAFT) {
            throw new BusinessRuleViolationException("Case is not in an editable input-change draft");
        }

        List<InputChangeRequestFileEntity> proposedFiles = requestFiles
                .findAllByRequest_IdAndFileSetAndRemovedAtIsNullOrderByAddedAtAsc(
                        requestId, InputChangeFileSet.PROPOSED);
        if (proposedFiles.isEmpty()) {
            throw new BusinessRuleViolationException("At least one completed INPUT file is required");
        }
        Instant now = clock.instant();
        for (InputChangeRequestFileEntity membership : proposedFiles) {
            StoredFileEntity file = membership.getFile();
            if (!request.getId().equals(membership.getRequest().getId())
                    || file.getFileKind() != FileKind.INPUT
                    || !file.getMedicalCase().getId().equals(caseId)) {
                throw new BusinessRuleViolationException("Proposal contains a file outside this case or request");
            }
            if (file.getConfirmedAt() == null) {
                throw new BusinessRuleViolationException("Every proposed upload must be completed before submission");
            }
            if (!storage.objectExists(file.getObjectKey())) {
                throw new BusinessRuleViolationException("A proposed INPUT file is missing from storage");
            }
        }

        request.submit(now);
        requests.saveAndFlush(request);
        medicalCase.setStatus(CaseStatus.INPUT_CHANGE_REVIEW);
        medicalCase.setUpdatedAt(now);
        cases.saveAndFlush(medicalCase);
        if (activityEvents != null) activityEvents.record("INPUT_CHANGE_SUBMITTED", now, caseId, actor.id(), actor.id(),
                "INPUT_CHANGE_REQUEST", requestId, "INPUT_CHANGE_SUBMITTED:" + requestId);
    }

    private MedicalCaseEntity ownedCase(AuthenticatedUser actor, UUID caseId, boolean lock) {
        if (actor == null || !actor.enabled() || actor.role() != Role.PATIENT) {
            throw new ForbiddenOperationException("Only enabled case owners can manage input changes");
        }
        MedicalCaseEntity medicalCase = (lock ? cases.findByIdForUpdate(caseId) : cases.findById(caseId))
                .orElseThrow(() -> new ResourceNotFoundException("Case not found"));
        if (!medicalCase.getPatient().getId().equals(actor.id())) {
            throw new ForbiddenOperationException("User does not own this case");
        }
        return medicalCase;
    }

    private InputChangeRequestEntity ownedRequest(AuthenticatedUser actor, UUID caseId, UUID requestId) {
        InputChangeRequestEntity request = requests.findByIdAndRequestedBy_Id(requestId, actor.id())
                .orElseThrow(() -> new ResourceNotFoundException("Input-change request not found"));
        if (!request.getMedicalCase().getId().equals(caseId)) {
            throw new ResourceNotFoundException("Input-change request not found");
        }
        return request;
    }

    private InputChangeRequestEntity editableRequest(AuthenticatedUser actor,
            MedicalCaseEntity medicalCase, UUID requestId) {
        InputChangeRequestEntity request = ownedRequest(actor, medicalCase.getId(), requestId);
        if (medicalCase.getStatus() != CaseStatus.INPUT_CHANGE_DRAFT
                || request.getStatus() != InputChangeRequestStatus.DRAFT) {
            throw new BusinessRuleViolationException("Input-change request is not editable");
        }
        return request;
    }

    private void scheduleCleanup(InputChangeRequestEntity request, StoredFileEntity file, Instant now) {
        if (!file.isInputActive() && !cleanup.existsById(file.getId())) {
            cleanup.save(new InputChangeFileCleanupEntity(file, request, now));
        }
    }

    public RequestView view(InputChangeRequestEntity request) {
        return new RequestView(request.getId(), request.getMedicalCase().getId(),
                request.getRequestedBy().getId(), request.getStatus(), request.getReason(),
                request.getResumeCaseStatus(),
                request.originalFiles().stream().map(m -> fileView(m.getFile())).toList(),
                request.proposedFiles().stream().map(InputChangeRequestFileEntity::getFile)
                        .filter(file -> file.isInputActive() || file.getConfirmedAt() != null)
                        .map(InputChangeDraftService::fileView).toList(),
                request.getCreatedAt(), request.getUpdatedAt(), request.getSubmittedAt(),
                request.getDecidedAt(), request.getDecidedBy() == null ? null : request.getDecidedBy().getId(),
                request.getDecisionReason());
    }

    private static FileView fileView(StoredFileEntity file) {
        return new FileView(file.getId(), file.getOriginalFilename(), file.getContentType(),
                file.getSizeBytes(), file.getFileKind());
    }

    public record FileView(UUID id, String originalFilename, String contentType,
            Long sizeBytes, FileKind fileKind) { }
    public record UploadIntent(FileView file, String uploadUrl, Instant expiresAt) { }
    public record RequestView(UUID id, UUID caseId, UUID requestedBy,
            InputChangeRequestStatus status, String reason, CaseStatus resumeCaseStatus,
            List<FileView> originalFiles, List<FileView> proposedFiles,
            Instant createdAt, Instant updatedAt, Instant submittedAt, Instant decidedAt,
            UUID decidedBy, String decisionReason) { }
}
