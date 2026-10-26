package com.healthdocs.backend.application.service;

import com.healthdocs.backend.adapter.out.persistence.CaseAssignmentRepository;
import com.healthdocs.backend.adapter.out.persistence.CaseResultRepository;
import com.healthdocs.backend.adapter.out.persistence.MedicalCaseRepository;
import com.healthdocs.backend.adapter.out.persistence.AppUserRepository;
import com.healthdocs.backend.adapter.out.persistence.StoredFileRepository;
import com.healthdocs.backend.adapter.out.persistence.entity.CaseResultEntity;
import com.healthdocs.backend.adapter.out.persistence.entity.CaseAssignmentEntity;
import com.healthdocs.backend.adapter.out.persistence.entity.MedicalCaseEntity;
import com.healthdocs.backend.adapter.out.persistence.entity.StoredFileEntity;
import com.healthdocs.backend.application.port.out.AuthProvider.AuthenticatedUser;
import com.healthdocs.backend.application.port.out.StoragePort;
import com.healthdocs.backend.domain.model.CaseStatus;
import com.healthdocs.backend.domain.model.FileKind;
import com.healthdocs.backend.domain.model.Role;
import com.healthdocs.backend.domain.model.ResultPublicationType;
import com.healthdocs.backend.domain.model.ResultStatus;
import com.healthdocs.backend.infrastructure.config.InternalFlowProperties;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

@Service
public class InternalFlowService {
    private final MedicalCaseRepository cases; private final StoredFileRepository files;
    private final CaseAssignmentRepository assignments; private final CaseResultRepository results;
    private final StoragePort storage; private final InternalFlowProperties properties; private final Clock clock;
    private final AppUserRepository users;
    private com.healthdocs.backend.application.port.out.ActivityEventPort activityEvents;
    private com.healthdocs.backend.application.port.out.SecurityAuditEventPort securityAudit;
    private com.healthdocs.backend.application.port.out.NotificationOutboxPort notificationOutbox;
    public InternalFlowService(MedicalCaseRepository cases, StoredFileRepository files, CaseAssignmentRepository assignments,
            CaseResultRepository results, StoragePort storage, InternalFlowProperties properties, Clock clock) {
        this(cases, files, assignments, results, storage, properties, clock, null);
    }
    @Autowired
    public InternalFlowService(MedicalCaseRepository cases, StoredFileRepository files, CaseAssignmentRepository assignments,
            CaseResultRepository results, StoragePort storage, InternalFlowProperties properties, Clock clock,
            AppUserRepository users) {
        this.cases=cases; this.files=files; this.assignments=assignments; this.results=results; this.storage=storage; this.properties=properties; this.clock=clock;
        this.users=users;
    }
    @Autowired
    public void setActivityEventRecorder(
            com.healthdocs.backend.application.port.out.ActivityEventPort activityEvents) {
        this.activityEvents = activityEvents;
    }
    @Autowired
    public void setSecurityAuditEventPort(
            com.healthdocs.backend.application.port.out.SecurityAuditEventPort securityAudit) {
        this.securityAudit = securityAudit;
    }
    @Autowired
    public void setNotificationOutboxPort(
            com.healthdocs.backend.application.port.out.NotificationOutboxPort notificationOutbox) {
        this.notificationOutbox = notificationOutbox;
    }
    @Transactional(readOnly = true)
    public List<CaseView> list(AuthenticatedUser user) {
        requireResearcher(user);
        return cases.findAssignedInternalCases(user.id(),
                List.of(CaseStatus.ENABLED_FOR_PROCESSING, CaseStatus.INPUT_CHANGE_DRAFT,
                        CaseStatus.INPUT_CHANGE_REVIEW, CaseStatus.FILES_RECEIVED,
                        CaseStatus.RESULT_UPLOADED, CaseStatus.RESULT_AVAILABLE))
                .stream().map(this::view).toList();
    }
    @Transactional(readOnly = true)
    public ResearcherCaseDetail detail(AuthenticatedUser user, UUID caseId) {
        requireResearcher(user);
        MedicalCaseEntity medicalCase = cases.findById(caseId)
                .filter(item -> List.of(CaseStatus.FILES_RECEIVED, CaseStatus.INPUT_CHANGE_DRAFT,
                        CaseStatus.INPUT_CHANGE_REVIEW, CaseStatus.ENABLED_FOR_PROCESSING,
                        CaseStatus.RESULT_UPLOADED, CaseStatus.RESULT_AVAILABLE).contains(item.getStatus()))
                .orElseThrow(() -> new ResourceNotFoundException("Case not found"));
        if (!assignments.existsByCaseIdAndUserId(caseId, user.id())) {
            throw new ResourceNotFoundException("Case not found");
        }

        List<ResearcherInputFile> inputFiles = files
                .findByMedicalCaseIdAndFileKindAndInputActiveTrue(caseId, FileKind.INPUT).stream()
                .filter(file -> file.getConfirmedAt() != null)
                .sorted(Comparator.comparing(StoredFileEntity::getCreatedAt,
                        Comparator.nullsLast(Comparator.naturalOrder())))
                .map(file -> new ResearcherInputFile(file.getId(), file.getOriginalFilename(),
                        file.getContentType(), file.getSizeBytes(), file.getCreatedAt()))
                .toList();
        if (inputFiles.isEmpty()) throw new ResourceNotFoundException("Case not found");

        List<ResearcherCaseResult> caseResults = results.findAcademicDetailResults(caseId).stream()
                .map(result -> new ResearcherCaseResult(result.getId(), result.getFile().getOriginalFilename(),
                        result.getFile().getContentType(), result.getFile().getSizeBytes(), result.getUploadedAt(),
                        result.getUpdatedAt(), result.getVersionNumber(), result.getStatus(),
                        result.getCorrectionRequest(), result.getPublicationType(), result.getAvailableAt()))
                .toList();
        UUID availableResultId = caseResults.stream()
                .filter(result -> result.status() == ResultStatus.AVAILABLE)
                .map(ResearcherCaseResult::id)
                .findFirst().orElse(null);
        ResearcherCaseInfo caseInfo = new ResearcherCaseInfo(medicalCase.getId(), medicalCase.getReferenceCode(),
                medicalCase.getStatus(), medicalCase.getStudyName(), medicalCase.getAdditionalReference(),
                medicalCase.getOriginInstitution(), medicalCase.getStudyDate(), medicalCase.getNotes(),
                medicalCase.getCreatedAt(), medicalCase.getUpdatedAt(), medicalCase.getReceivedAt(), availableResultId);
        List<ResearcherAccess> researchers = List.of();
        if (users != null) {
            List<CaseAssignmentEntity> caseAssignments = assignments.findAllByCaseId(caseId);
            Set<UUID> assignedIds = caseAssignments.stream().map(CaseAssignmentEntity::getUserId).collect(Collectors.toSet());
            Map<UUID, com.healthdocs.backend.adapter.out.persistence.entity.AppUserEntity> assignedUsers = users.findAllById(assignedIds)
                    .stream().collect(Collectors.toMap(com.healthdocs.backend.adapter.out.persistence.entity.AppUserEntity::getId, account -> account));
            researchers = caseAssignments.stream()
                    .map(assignment -> {
                        var account = assignedUsers.get(assignment.getUserId());
                        return account == null || account.getRole() != Role.RESEARCHER || !account.isEnabled()
                                ? null
                                : new ResearcherAccess(account.getId(), account.getFullName(), account.getResearcherCode(),
                                        account.getAuthorizationStatus(), assignment.getAssignedAt());
                    })
                    .filter(java.util.Objects::nonNull)
                    .sorted(Comparator.comparing(ResearcherAccess::fullName, Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER)))
                    .toList();
        }
        return new ResearcherCaseDetail(caseInfo, inputFiles, researchers, caseResults);
    }
    @Transactional
    public Download download(AuthenticatedUser user, UUID caseId, UUID fileId) {
        requireInternal(user);
        MedicalCaseEntity medicalCase = downloadVisibleCase(user, caseId);
        StoredFileEntity file = files.findById(fileId).orElseThrow(() -> new ResourceNotFoundException("File not found"));
        if (!file.getMedicalCase().getId().equals(medicalCase.getId())
                || file.getFileKind() != FileKind.INPUT || !file.isInputActive())
            throw new ResourceNotFoundException("File not found");
        StoragePort.UploadUrlResult url=storage.generateDownloadUrl(file.getObjectKey(), file.getContentType(), properties.downloadTtlMinutes());
        recordSensitiveAccess(user, caseId, "INPUT_FILE", fileId);
        return new Download(url.uploadUrl(), url.expiresAt());
    }
    @Transactional
    public PatientResult patientResult(AuthenticatedUser user, UUID caseId) {
        if (user == null || !user.enabled() || user.role() != Role.PATIENT) {
            throw new ForbiddenOperationException("Patient access required");
        }
        MedicalCaseEntity medicalCase = cases.findById(caseId)
                .orElseThrow(() -> new ResourceNotFoundException("Case not found"));
        if (!medicalCase.getPatient().getId().equals(user.id())) {
            throw new ForbiddenOperationException("Case does not belong to patient");
        }
        if (medicalCase.getStatus() != CaseStatus.RESULT_AVAILABLE) {
            throw new BusinessRuleViolationException("Case result is not available yet");
        }
        CaseResultEntity result = results.findByMedicalCaseIdAndCurrentTrueAndDeletedAtIsNull(caseId)
                .orElseThrow(() -> new ResourceNotFoundException("Result not available"));
        if (result.getStatus() != ResultStatus.AVAILABLE || !result.isCurrent() || result.isDeleted()
                || result.getVersionNumber() == null || result.getPublicationType() == null) {
            throw new BusinessRuleViolationException("Result is not available yet");
        }
        StoredFileEntity file = result.getFile();
        if (file == null
                || file.getFileKind() != FileKind.RESULT
                || file.getMedicalCase() == null
                || !caseId.equals(file.getMedicalCase().getId())
                || file.getObjectKey() == null
                || file.getObjectKey().isBlank()
                || !storage.objectExists(file.getObjectKey())) {
            throw new ResourceNotFoundException("Result file not found");
        }
        StoragePort.UploadUrlResult url = storage.generateDownloadUrl(
                file.getObjectKey(), file.getContentType(), properties.downloadTtlMinutes());
        recordSensitiveAccess(user, caseId, "RESULT", result.getId());
        return new PatientResult(result.getId(), caseId, result.getAvailableAt(),
                new Download(url.uploadUrl(), url.expiresAt()));
    }

    @Transactional(readOnly = true)
    public PatientResults patientResults(AuthenticatedUser user, String q, String sort, int page, int size,
            ResultPublicationType publicationType) {
        requirePatient(user);
        if (page < 0 || size < 1 || size > 100) throw new IllegalArgumentException("Invalid page or size");
        String query = q == null ? "" : q.trim().toLowerCase(java.util.Locale.ROOT);
        if (query.length() > 200) throw new IllegalArgumentException("q is too long");
        String[] sortParts = sort == null || sort.isBlank() ? new String[] {"availableAt", "desc"} : sort.split(",", -1);
        if (sortParts.length != 2 || !List.of("availableAt", "caseReferenceCode", "studyName", "fileName").contains(sortParts[0]))
            throw new IllegalArgumentException("Unsupported sort");
        Sort.Direction direction;
        try { direction = Sort.Direction.fromString(sortParts[1]); }
        catch (IllegalArgumentException ex) { throw new IllegalArgumentException("Unsupported sort direction"); }
        String property = switch (sortParts[0]) {
            case "caseReferenceCode" -> "medicalCase.referenceCode";
            case "studyName" -> "medicalCase.studyName";
            case "fileName" -> "file.originalFilename";
            default -> "availableAt";
        };
        Page<CaseResultEntity> resultPage = results.findPublishedPatientResults(user.id(), publicationType, query,
                PageRequest.of(page, size, Sort.by(direction, property).and(Sort.by(Sort.Direction.DESC, "id"))));
        List<PatientResultItem> items = resultPage.getContent().stream().map(result -> {
            String filename = result.getFile().getOriginalFilename();
            int dot = filename == null ? -1 : filename.lastIndexOf('.');
            String extension = dot > 0 && dot < filename.length() - 1 ? filename.substring(dot + 1) : "";
            return new PatientResultItem(result.getId(), result.getMedicalCase().getId(),
                    result.getMedicalCase().getReferenceCode(), result.getMedicalCase().getStudyName(), filename,
                    extension, result.getFile().getContentType(), result.getAvailableAt(), result.getPublicationType());
        }).toList();
        return new PatientResults(new PatientResultSummary(results.countPublishedPatientResults(user.id()),
                results.latestPublishedPatientResult(user.id())), items, resultPage.getTotalElements(), page, size);
    }

    private void requirePatient(AuthenticatedUser user) {
        if (user == null || !user.enabled() || user.role() != Role.PATIENT)
            throw new ForbiddenOperationException("Patient access required");
    }
    @Transactional
    public Download downloadResult(AuthenticatedUser user, UUID caseId, UUID resultId) {
        // Coordinadores e investigadores comparten la descarga temporal del resultado:
        // el acceso se limita al personal interno asignado al caso.
        requireInternal(user);
        visibleResultCase(user, caseId, resultId);
        CaseResultEntity result = results.findById(resultId).orElseThrow(() -> new ResourceNotFoundException("Result not found"));
        if (result.isDeleted()) throw new ResourceNotFoundException("Result not found");
        StoragePort.UploadUrlResult url = storage.generateDownloadUrl(result.getFile().getObjectKey(), result.getFile().getContentType(), properties.downloadTtlMinutes());
        recordSensitiveAccess(user, caseId, "RESULT", resultId);
        return new Download(url.uploadUrl(), url.expiresAt());
    }

    private void recordSensitiveAccess(AuthenticatedUser user, UUID caseId, String resourceType, UUID resourceId) {
        if (securityAudit != null) {
            securityAudit.record("SENSITIVE_RESOURCE_ACCESS_GRANTED", clock.instant(), user.id(), user.id(), caseId,
                    resourceType, resourceId, "DOWNLOAD_URL_ISSUED", null, null, null,
                    "SENSITIVE_RESOURCE_ACCESS_GRANTED:" + UUID.randomUUID());
        }
    }
    @Transactional
    public void deleteResult(AuthenticatedUser user, UUID caseId, UUID resultId, String reason) {
        requireAuthorizedResearcher(user);
        String normalizedReason = normalizeReason(reason, "Deletion reason is required");
        CaseResultEntity result = results.findById(resultId).orElseThrow(() -> new ResourceNotFoundException("Result not found"));
        visibleResultCase(user, caseId, resultId);
        MedicalCaseEntity medicalCase = cases.findByIdForUpdate(caseId).orElse(result.getMedicalCase());
        boolean awaitingReview = result.getStatus() == ResultStatus.UPLOADED
                && medicalCase.getStatus() == CaseStatus.RESULT_UPLOADED;
        boolean correctionRequired = result.getStatus() == ResultStatus.CORRECTION_REQUIRED
                && medicalCase.getStatus() == CaseStatus.ENABLED_FOR_PROCESSING;
        if (result.isDeleted() || !result.isCurrent() || (!awaitingReview && !correctionRequired)) {
            throw new BusinessRuleViolationException("Only the current unpublished or correction-required result can be deleted");
        }
        Instant deletedAt = clock.instant();
        result.softDelete(deletedAt, user.id(), normalizedReason);
        results.save(result);
        // El reemplazo es explícito: primero se retira la versión pendiente y
        // luego el caso queda habilitado para iniciar una nueva carga.
        medicalCase.setStatus(CaseStatus.ENABLED_FOR_PROCESSING);
        medicalCase.setUpdatedAt(deletedAt);
        cases.save(medicalCase);
        if (activityEvents != null) activityEvents.record("RESULT_DELETED", deletedAt, caseId, user.id(),
                medicalCase.getPatient().getId(), "RESULT", resultId, "RESULT_DELETED:" + resultId);
    }

    @Transactional
    public void requestCorrection(AuthenticatedUser user, UUID resultId, String reason) {
        requireCoordinator(user);
        String normalizedReason = normalizeReason(reason, "Correction reason is required");
        CaseResultEntity result = results.findById(resultId)
                .orElseThrow(() -> new ResourceNotFoundException("Result not found"));
        MedicalCaseEntity medicalCase = cases.findByIdForUpdate(result.getMedicalCase().getId())
                .orElse(result.getMedicalCase());
        if (!coordinatorCanAccessCase(user, medicalCase.getId())) {
            throw new ForbiddenOperationException("Case is not available");
        }
        boolean awaitingReview = result.getStatus() == ResultStatus.UPLOADED
                && medicalCase.getStatus() == CaseStatus.RESULT_UPLOADED;
        boolean published = result.getStatus() == ResultStatus.AVAILABLE
                && medicalCase.getStatus() == CaseStatus.RESULT_AVAILABLE;
        if (result.isDeleted() || !result.isCurrent() || (!awaitingReview && !published)) {
            throw new BusinessRuleViolationException("Only the current uploaded or published result can be returned for correction");
        }
        Instant requestedAt = clock.instant();
        result.requestCorrection(normalizedReason);
        medicalCase.setStatus(CaseStatus.ENABLED_FOR_PROCESSING);
        medicalCase.setUpdatedAt(requestedAt);
        results.save(result);
        cases.save(medicalCase);
        if (activityEvents != null) activityEvents.record("RESULT_CORRECTION_REQUESTED", requestedAt,
                medicalCase.getId(), user.id(), medicalCase.getPatient().getId(), "RESULT", resultId,
                "RESULT_CORRECTION_REQUESTED:" + resultId);
    }
    private MedicalCaseEntity visibleResultCase(AuthenticatedUser user, UUID caseId, UUID resultId) {
        MedicalCaseEntity c = cases.findById(caseId).orElseThrow(() -> new ResourceNotFoundException("Case not found"));
        if (user.role() != Role.COORDINATOR && !assignments.existsByCaseIdAndUserId(caseId, user.id())) throw new ForbiddenOperationException("Case is not available");
        CaseResultEntity result = results.findById(resultId).orElseThrow(() -> new ResourceNotFoundException("Result not found"));
        if (!result.getMedicalCase().getId().equals(caseId)) throw new ResourceNotFoundException("Result not found");
        return c;
    }
    @Transactional
    public UploadIntent createResult(AuthenticatedUser user, UUID caseId, String filename, String contentType, Long size) {
        requireAuthorizedResearcher(user); visibleCase(user, caseId);
        // Un archivo RESULT creado durante una intención pendiente no es todavía
        // un resultado asociado. Solo un case_result confirmado debe bloquear
        // una nueva intención; de lo contrario un intento expirado o fallido
        // impediría reintentar la carga.
        if (results.existsByMedicalCaseIdAndStatusAndDeletedAtIsNull(caseId, ResultStatus.UPLOADED)) {
            throw new BusinessRuleViolationException("A result upload is already awaiting review");
        }
        validate(filename, contentType, size);
        UUID id=UUID.randomUUID(); String key="result/%s/%s".formatted(caseId,id); Instant now=clock.instant();
        StoredFileEntity entity=new StoredFileEntity(id, cases.findById(caseId).orElseThrow(), key, FileKind.RESULT,
                filename.trim(), contentType.trim(), size, user.id());
        StoredFileEntity saved=files.save(entity); StoragePort.UploadUrlResult url=storage.generateUploadUrl(key, contentType, properties.uploadTtlMinutes());
        return new UploadIntent(
                new FileView(saved.getId(), saved.getOriginalFilename(), saved.getContentType(), saved.getSizeBytes(), saved.getFileKind()),
                url.uploadUrl(), url.expiresAt());
    }
    @Transactional
    public Confirmation confirm(AuthenticatedUser user, UUID fileId) {
        requireAuthorizedResearcher(user); StoredFileEntity file=files.findById(fileId).orElseThrow(()->new ResourceNotFoundException("File not found"));
        if (file.getFileKind()!=FileKind.RESULT) throw new BusinessRuleViolationException("Only result files can be confirmed internally");
        if (file.getUploadInitiatedBy() == null || !file.getUploadInitiatedBy().equals(user.id())) {
            throw new ForbiddenOperationException("Only the researcher who initiated the upload can complete it");
        }
        UUID caseId = file.getMedicalCase().getId();
        MedicalCaseEntity medicalCase = cases.findByIdForUpdate(caseId)
                .orElseThrow(() -> new ResourceNotFoundException("Case not found"));
        if (medicalCase.getStatus() != CaseStatus.ENABLED_FOR_PROCESSING
                || !assignments.existsByCaseIdAndUserId(caseId, user.id())) {
            throw new ForbiddenOperationException("Case is not available");
        }
        if (!storage.objectExists(file.getObjectKey())) throw new BusinessRuleViolationException("Uploaded object does not exist");
        if (results.existsByMedicalCaseIdAndStatusAndDeletedAtIsNull(caseId, ResultStatus.UPLOADED)) {
            throw new BusinessRuleViolationException("A result upload is already awaiting review");
        }
        CaseResultEntity previous = results.findByMedicalCaseIdAndCurrentTrueAndDeletedAtIsNull(caseId).orElse(null);
        if (previous != null && previous.getStatus() == ResultStatus.CORRECTION_REQUIRED) {
            // The request is fulfilled by the corrected upload. Its reason is
            // cleared per D-08 and the old version becomes non-current history.
            previous.resolveCorrectionAsHistory();
            results.save(previous);
        }
        CaseResultEntity result=results.save(new CaseResultEntity(medicalCase,file,ResultStatus.UPLOADED));
        int nextVersion = results.findMaximumVersionNumber(caseId) + 1;
        ResultPublicationType publicationType = nextVersion == 1
                ? ResultPublicationType.INITIAL : ResultPublicationType.CORRECTED;
        Instant uploadedAt = clock.instant();
        result.recordUpload(user.id(), uploadedAt);
        result.reserveVersion(nextVersion, publicationType);
        medicalCase.setStatus(CaseStatus.RESULT_UPLOADED); medicalCase.setUpdatedAt(uploadedAt); cases.save(medicalCase);
        if (activityEvents != null) {
            String type = publicationType == ResultPublicationType.CORRECTED
                    ? "RESULT_CORRECTION_UPLOADED" : "RESULT_UPLOADED";
            activityEvents.record(type, uploadedAt, caseId, user.id(), medicalCase.getPatient().getId(),
                    "RESULT", result.getId(), type + ":" + result.getId() + ":" + nextVersion);
        }
        return new Confirmation(
                new FileView(file.getId(), file.getOriginalFilename(), file.getContentType(), file.getSizeBytes(), file.getFileKind()),
                new UploadedResult(result.getId(), medicalCase.getId(), result.getStatus(), user.id(), result.getUploadedAt(),
                        result.getVersionNumber(), result.getPublicationType(), result.getCorrectionRequest()),
                medicalCase.getStatus());
    }
    @Transactional
    public void makeResultAvailable(AuthenticatedUser user, UUID resultId) {
        requireCoordinator(user);
        CaseResultEntity result = results.findById(resultId)
                .orElseThrow(() -> new ResourceNotFoundException("Result not found"));
        MedicalCaseEntity medicalCase = cases.findByIdForUpdate(result.getMedicalCase().getId())
                .orElse(result.getMedicalCase());
        if (!coordinatorCanAccessCase(user, medicalCase.getId())) {
            throw new ForbiddenOperationException("Case is not available");
        }
        if (result.isDeleted() || result.getStatus() != ResultStatus.UPLOADED
                || medicalCase.getStatus() != CaseStatus.RESULT_UPLOADED) {
            throw new BusinessRuleViolationException("Result is not ready to be published");
        }
        Instant now = clock.instant();
        CaseResultEntity previous = results.findByMedicalCaseIdAndCurrentTrueAndDeletedAtIsNull(medicalCase.getId()).orElse(null);
        if (previous != null && !previous.getId().equals(resultId)) {
            previous.markNotCurrent();
            results.save(previous);
        }
        int versionNumber = result.getVersionNumber() != null
                ? result.getVersionNumber() : results.findMaximumVersionNumber(medicalCase.getId()) + 1;
        ResultPublicationType publicationType = result.getPublicationType() != null
                ? result.getPublicationType()
                : versionNumber == 1 ? ResultPublicationType.INITIAL : ResultPublicationType.CORRECTED;
        result.publishAsVersion(versionNumber, publicationType, now, user.id());
        medicalCase.makeResultAvailable(now);
        results.save(result);
        cases.save(medicalCase);
        String eventKey = "RESULT_PUBLISHED:" + result.getId() + ":" + versionNumber;
        if (activityEvents != null) {
            activityEvents.record("RESULT_PUBLISHED", now, medicalCase.getId(), user.id(),
                    medicalCase.getPatient().getId(), "RESULT", result.getId(),
                    eventKey);
        }
        if (notificationOutbox != null) {
            notificationOutbox.enqueue("RESULT_PUBLISHED", eventKey, medicalCase.getId(),
                    medicalCase.getPatient().getId(), user.id(), "RESULT", result.getId(), now);
        }
    }
    private MedicalCaseEntity visibleCase(AuthenticatedUser user, UUID id) { MedicalCaseEntity c=cases.findById(id).orElseThrow(()->new ResourceNotFoundException("Case not found")); if(c.getStatus()!=CaseStatus.ENABLED_FOR_PROCESSING || !assignments.existsByCaseIdAndUserId(id,user.id())) throw new ForbiddenOperationException("Case is not available"); return c; }
    private boolean coordinatorCanAccessCase(AuthenticatedUser user, UUID caseId) {
        return user.role() == Role.COORDINATOR || assignments.existsByCaseIdAndUserId(caseId, user.id());
    }
    private MedicalCaseEntity downloadVisibleCase(AuthenticatedUser user, UUID id) {
        MedicalCaseEntity c = cases.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Case not found"));
        // El insumo sigue siendo necesario para revisar, comparar o reprocesar el caso
        // incluso después de cargar o publicar el resultado. Por eso la autorización
        // de descarga cubre todo el ciclo de trabajo posterior a la asignación.
        boolean assignedCase = c.getStatus() == CaseStatus.ENABLED_FOR_PROCESSING
                || c.getStatus() == CaseStatus.INPUT_CHANGE_DRAFT
                || c.getStatus() == CaseStatus.INPUT_CHANGE_REVIEW
                || c.getStatus() == CaseStatus.FILES_RECEIVED
                || c.getStatus() == CaseStatus.RESULT_UPLOADED
                || c.getStatus() == CaseStatus.RESULT_AVAILABLE;
        boolean assignedInternalCase = assignedCase && (user.role() == Role.COORDINATOR
                || assignments.existsByCaseIdAndUserId(id, user.id()));
        boolean coordinatorReviewingPendingCase = user.role() == Role.COORDINATOR
                && c.getStatus() == CaseStatus.FILES_RECEIVED;
        if (!assignedInternalCase && !coordinatorReviewingPendingCase) {
            throw new ForbiddenOperationException("Case is not available");
        }
        return c;
    }
    private void requireInternal(AuthenticatedUser u) { if(u==null || !u.enabled() || (u.role()!=Role.RESEARCHER && u.role()!=Role.COORDINATOR)) throw new ForbiddenOperationException("Internal access required"); }
    private void requireResearcher(AuthenticatedUser user) {
        if (user == null || !user.enabled() || user.role() != Role.RESEARCHER) {
            throw new ForbiddenOperationException("Researcher access required");
        }
    }
    private void requireAuthorizedResearcher(AuthenticatedUser user) {
        if (user == null || !user.enabled() || user.role() != Role.RESEARCHER || users == null
                || users.findById(user.id()).filter(account -> account.isEnabled()
                        && account.getAuthorizationStatus() == com.healthdocs.backend.domain.model.ResearcherAuthorizationStatus.AUTHORIZED)
                        .isEmpty()) {
            throw new ForbiddenOperationException("An authorized researcher account is required");
        }
    }
    private void requireCoordinator(AuthenticatedUser u) { if(u==null || !u.enabled() || u.role()!=Role.COORDINATOR) throw new ForbiddenOperationException("Coordinator access required"); }
    private void validate(String f,String m,Long s) { if(f==null||f.isBlank()||f.length()>255||f.contains("..")||f.contains("/")||f.contains("\\")) throw new IllegalArgumentException("Invalid originalFilename"); if(m==null||!properties.allowedMimeTypes().contains(m.split(";",2)[0].trim())) throw new UnsupportedMediaTypeException("MIME type is not allowed"); if(s==null||s<1) throw new IllegalArgumentException("sizeBytes must be positive"); if(s>properties.resultMaxSizeBytes()) throw new PayloadTooLargeException("File exceeds maximum size"); }
    private String normalizeReason(String reason, String message) {
        if (reason == null || reason.isBlank()) throw new IllegalArgumentException(message);
        String normalized = reason.trim();
        if (normalized.length() > 4000) throw new IllegalArgumentException("Reason must not exceed 4000 characters");
        return normalized;
    }
    private CaseView view(MedicalCaseEntity c) {
        CaseResultEntity result = results.findByMedicalCaseId(c.getId()).orElse(null);
        ResultView resultView = result == null ? null : new ResultView(
                result.getId(), result.getFile().getOriginalFilename(), result.getStatus(),
                result.getUploadedBy(), result.getUploadedAt(), result.getAvailableAt());
        return new CaseView(c.getId(), c.getReferenceCode(), c.getStudyName(), c.getStatus(), c.getCreatedAt(), c.getUpdatedAt(),
                files.findByMedicalCaseIdAndFileKindAndInputActiveTrue(c.getId(), FileKind.INPUT).stream()
                        .map(f->new FileView(f.getId(),f.getOriginalFilename(),f.getContentType(),f.getSizeBytes(),f.getFileKind())).toList(),
                resultView);
    }
    private static String sanitize(String s){return s.replace("\\","_").replace("/","_");}
    public record FileView(UUID id,String originalFilename,String contentType,Long sizeBytes,FileKind fileKind){}
    public record CaseView(UUID id,String referenceCode,String studyName,CaseStatus status,Instant createdAt,Instant updatedAt,List<FileView> files,ResultView result){}
    public record ResearcherCaseDetail(@com.fasterxml.jackson.annotation.JsonProperty("case") ResearcherCaseInfo caseInfo,
            List<ResearcherInputFile> inputFiles, List<ResearcherAccess> researchers, List<ResearcherCaseResult> results) {}
    public record ResearcherAccess(UUID userId, String fullName, String researcherCode,
            com.healthdocs.backend.domain.model.ResearcherAuthorizationStatus authorizationStatus, Instant assignedAt) {}
    public record ResearcherCaseInfo(UUID id, String referenceCode, CaseStatus status, String studyName,
            String additionalReference, String originInstitution, LocalDate studyDate, String notes,
            Instant createdAt, Instant updatedAt, Instant receivedAt, UUID availableResultId) {}
    public record ResearcherInputFile(UUID id, String originalFilename, String contentType,
            Long sizeBytes, Instant uploadedAt) {}
    public record ResearcherCaseResult(UUID id, String originalFilename, String contentType, Long sizeBytes,
            Instant uploadedAt, Instant updatedAt, Integer version, ResultStatus status,
            String correctionRequest, ResultPublicationType publicationType, Instant availableAt) {}
    public record ResultView(UUID id,String originalFilename,com.healthdocs.backend.domain.model.ResultStatus status,UUID uploadedBy,Instant uploadedAt,Instant availableAt){}
    public record Download(String downloadUrl,Instant expiresAt){}
    public record PatientResult(UUID id, UUID caseId, Instant availableAt, Download download){}
    public record PatientResultSummary(long availableCaseCount, Instant latestPublishedAt) {}
    public record PatientResultItem(UUID resultId, UUID caseId, String caseReferenceCode, String studyName,
            String fileName, String fileExtension, String contentType, Instant availableAt,
            ResultPublicationType publicationType) {}
    public record PatientResults(PatientResultSummary summary, List<PatientResultItem> items, long totalItems,
            int page, int size) {}
    public record UploadIntent(FileView file,String uploadUrl,Instant expiresAt){}
    public record UploadedResult(UUID id, UUID caseId, ResultStatus status, UUID uploadedBy, Instant uploadedAt,
            Integer version, ResultPublicationType publicationType, String correctionRequest) { }
    public record Confirmation(FileView file,UploadedResult result,CaseStatus caseStatus){}
}
