package com.healthdocs.backend.application.service;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.healthdocs.backend.adapter.out.persistence.AppUserRepository;
import com.healthdocs.backend.adapter.out.persistence.CaseAssignmentRepository;
import com.healthdocs.backend.adapter.out.persistence.CaseResultRepository;
import com.healthdocs.backend.adapter.out.persistence.InputChangeRequestRepository;
import com.healthdocs.backend.adapter.out.persistence.MedicalCaseRepository;
import com.healthdocs.backend.adapter.out.persistence.StoredFileRepository;
import com.healthdocs.backend.adapter.out.persistence.entity.AppUserEntity;
import com.healthdocs.backend.adapter.out.persistence.entity.CaseAssignmentEntity;
import com.healthdocs.backend.adapter.out.persistence.entity.CaseResultEntity;
import com.healthdocs.backend.adapter.out.persistence.entity.InputChangeRequestEntity;
import com.healthdocs.backend.adapter.out.persistence.entity.MedicalCaseEntity;
import com.healthdocs.backend.adapter.out.persistence.entity.StoredFileEntity;
import com.healthdocs.backend.application.port.out.AuthProvider.AuthenticatedUser;
import com.healthdocs.backend.domain.model.CaseStatus;
import com.healthdocs.backend.domain.model.FileKind;
import com.healthdocs.backend.domain.model.InputChangeRequestStatus;
import com.healthdocs.backend.domain.model.ResearcherAuthorizationStatus;
import com.healthdocs.backend.domain.model.ResultStatus;
import com.healthdocs.backend.domain.model.Role;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Builds the coordinator-facing detail projection for one academic case. */
@Service
public class CoordinatorAcademicCaseDetailUseCase {
    private static final Set<CaseStatus> VISIBLE_STATUSES = Set.of(
            CaseStatus.FILES_RECEIVED,
            CaseStatus.ENABLED_FOR_PROCESSING,
            CaseStatus.INPUT_CHANGE_DRAFT,
            CaseStatus.INPUT_CHANGE_REVIEW,
            CaseStatus.RESULT_UPLOADED,
            CaseStatus.RESULT_AVAILABLE);

    private final MedicalCaseRepository cases;
    private final CaseAssignmentRepository assignments;
    private final AppUserRepository users;
    private final StoredFileRepository files;
    private final CaseResultRepository results;
    private final InputChangeRequestRepository inputChanges;

    public CoordinatorAcademicCaseDetailUseCase(
            MedicalCaseRepository cases,
            CaseAssignmentRepository assignments,
            AppUserRepository users,
            StoredFileRepository files,
            CaseResultRepository results,
            InputChangeRequestRepository inputChanges) {
        this.cases = cases;
        this.assignments = assignments;
        this.users = users;
        this.files = files;
        this.results = results;
        this.inputChanges = inputChanges;
    }

    @Transactional(readOnly = true)
    public CoordinatorAcademicCaseDetail execute(AuthenticatedUser actor, UUID caseId) {
        requireCoordinator(actor);
        MedicalCaseEntity medicalCase = cases.findById(caseId)
                .filter(item -> VISIBLE_STATUSES.contains(item.getStatus()))
                .orElseThrow(() -> new ResourceNotFoundException("Case not found"));

        List<StoredFileEntity> inputFiles = files
                .findByMedicalCaseIdAndFileKindAndInputActiveTrue(caseId, FileKind.INPUT).stream()
                .filter(file -> file.getConfirmedAt() != null)
                .sorted(Comparator.comparing(StoredFileEntity::getCreatedAt,
                        Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();
        if (inputFiles.isEmpty()) {
            throw new ResourceNotFoundException("Case not found");
        }

        List<CaseAssignmentEntity> caseAssignments = assignments.findAllByCaseId(caseId);
        Set<UUID> assignedUserIds = new LinkedHashSet<>();
        caseAssignments.forEach(assignment -> assignedUserIds.add(assignment.getUserId()));
        Map<UUID, AppUserEntity> assignedUsers = users.findAllById(assignedUserIds).stream()
                .collect(java.util.stream.Collectors.toMap(AppUserEntity::getId, user -> user));
        List<AcademicCaseResearcher> researchers = caseAssignments.stream()
                .map(assignment -> toResearcher(assignment, assignedUsers.get(assignment.getUserId())))
                .filter(java.util.Objects::nonNull)
                .sorted(Comparator.comparing(AcademicCaseResearcher::fullName,
                        Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER)))
                .toList();

        boolean canReviewResults = assignments.existsByCaseIdAndUserId(caseId, actor.id());
        List<CaseResultEntity> visibleResults = canReviewResults
                ? results.findAcademicDetailResults(caseId)
                : List.of();
        Map<UUID, AppUserEntity> resultUploaders = users.findAllById(visibleResults.stream()
                        .map(CaseResultEntity::getUploadedBy)
                        .filter(java.util.Objects::nonNull)
                        .collect(java.util.stream.Collectors.toSet()))
                .stream().collect(java.util.stream.Collectors.toMap(AppUserEntity::getId, user -> user));
        List<CoordinatorCaseResult> resultViews = visibleResults.stream()
                .map(result -> toResult(result, resultUploaders.get(result.getUploadedBy()), medicalCase))
                .toList();

        List<AcademicCaseHistoryEvent> history = buildHistory(
                medicalCase, caseAssignments, assignedUsers, visibleResults, canReviewResults);
        UUID availableResultId = resultViews.stream()
                .filter(result -> result.status() == ResultStatus.AVAILABLE)
                .max(Comparator.comparing(CoordinatorCaseResult::availableAt,
                        Comparator.nullsLast(Comparator.naturalOrder())))
                .map(CoordinatorCaseResult::id)
                .orElse(null);

        CoordinatorAcademicCaseInfo caseInfo = new CoordinatorAcademicCaseInfo(
                medicalCase.getId(), medicalCase.getReferenceCode(), medicalCase.getStatus(),
                medicalCase.getStudyName(), medicalCase.getAdditionalReference(),
                medicalCase.getOriginInstitution(), medicalCase.getStudyDate(), medicalCase.getNotes(),
                medicalCase.getCreatedAt(), medicalCase.getUpdatedAt(), availableResultId,
                medicalCase.getReceivedAt());
        return new CoordinatorAcademicCaseDetail(caseInfo,
                inputFiles.stream().map(this::toInputFile).toList(), researchers,
                resultViews, history, canReviewResults);
    }

    private AcademicCaseResearcher toResearcher(CaseAssignmentEntity assignment, AppUserEntity user) {
        if (user == null || user.getRole() != Role.RESEARCHER) return null;
        return new AcademicCaseResearcher(user.getId(), user.getFullName(), user.getEmail(),
                user.getAuthorizationStatus(), assignment.getAssignedAt());
    }

    private AcademicCaseInputFile toInputFile(StoredFileEntity file) {
        return new AcademicCaseInputFile(file.getId(), file.getOriginalFilename(), file.getContentType(),
                file.getSizeBytes(), file.getConfirmedAt());
    }

    private CoordinatorCaseResult toResult(CaseResultEntity result, AppUserEntity uploader,
            MedicalCaseEntity medicalCase) {
        var file = result.getFile();
        int version = result.getVersionNumber() != null
                ? result.getVersionNumber()
                : results.findMaximumVersionNumber(medicalCase.getId()) + 1;
        String extension = extension(file.getOriginalFilename());
        CoordinatorResultFile resultFile = new CoordinatorResultFile(file.getId(), file.getOriginalFilename(),
                file.getContentType(), file.getSizeBytes(), extension);
        CoordinatorResultCase resultCase = new CoordinatorResultCase(medicalCase.getId(),
                medicalCase.getReferenceCode(), medicalCase.getStudyName());
        AcademicCaseUploader uploadedBy = uploader == null ? null
                : new AcademicCaseUploader(uploader.getId(), uploader.getResearcherCode(), uploader.getFullName());
        String correctionRequest = result.getStatus() == ResultStatus.CORRECTION_REQUIRED
                ? result.getCorrectionRequest() : null;
        return new CoordinatorCaseResult(result.getId(), resultFile, resultCase, uploadedBy,
                result.getUploadedAt(), result.getUpdatedAt(), version, result.getStatus(),
                correctionRequest, result.getPublicationType(), result.getAvailableAt(),
                result.getDeletedAt(), result.getDeletionReason());
    }

    private List<AcademicCaseHistoryEvent> buildHistory(MedicalCaseEntity medicalCase,
            List<CaseAssignmentEntity> caseAssignments, Map<UUID, AppUserEntity> assignedUsers,
            List<CaseResultEntity> visibleResults, boolean canReviewResults) {
        List<AcademicCaseHistoryEvent> events = new ArrayList<>();
        addEvent(events, "CASE_CREATED", medicalCase.getCreatedAt(), "Caso creado",
                null, medicalCase.getPatient().getId());
        addEvent(events, "FILES_RECEIVED", medicalCase.getReceivedAt(), "Archivos recibidos",
                null, null);

        for (CaseAssignmentEntity assignment : caseAssignments) {
            AppUserEntity user = assignedUsers.get(assignment.getUserId());
            if (user == null || user.getRole() != Role.RESEARCHER) continue;
            addEvent(events, "RESEARCHER_ASSIGNED", assignment.getAssignedAt(),
                    "Acceso asignado a " + safeName(user), user.getId(), null);
        }

        if (canReviewResults) {
            for (CaseResultEntity result : visibleResults) {
                addEvent(events, "RESULT_UPLOADED", result.getUploadedAt(),
                        "Resultado cargado: " + result.getFile().getOriginalFilename(),
                        result.getId(), result.getUploadedBy());
                if (result.getStatus() == ResultStatus.AVAILABLE) {
                    addEvent(events, "RESULT_AVAILABLE", result.getAvailableAt(),
                            "Resultado publicado: " + result.getFile().getOriginalFilename(),
                            result.getId(), result.getAvailableBy());
                } else if (result.getStatus() == ResultStatus.CORRECTION_REQUIRED) {
                    addEvent(events, "RESULT_CORRECTION_REQUESTED", result.getUpdatedAt(),
                            "Se solicitó corregir el resultado: " + result.getCorrectionRequest(),
                            result.getId(), null);
                }
            }
        }

        for (InputChangeRequestEntity request : inputChanges
                .findAllByMedicalCase_IdOrderByCreatedAtAsc(medicalCase.getId())) {
            addEvent(events, "INPUT_CHANGE_STARTED", request.getCreatedAt(),
                    "Solicitud de cambio iniciada", request.getId(), request.getRequestedBy().getId());
            addEvent(events, "INPUT_CHANGE_SUBMITTED", request.getSubmittedAt(),
                    "Solicitud enviada a coordinación", request.getId(), request.getRequestedBy().getId());
            if (request.getDecidedAt() != null) {
                String decision = request.getStatus() == InputChangeRequestStatus.APPROVED
                        ? "aprobada" : "rechazada";
                addEvent(events, "INPUT_CHANGE_DECIDED", request.getDecidedAt(),
                        "Solicitud " + decision, request.getId(),
                        request.getDecidedBy() == null ? null : request.getDecidedBy().getId());
            }
            addEvent(events, "INPUT_CHANGE_CANCELLED", request.getCancelledAt(),
                    "Solicitud cancelada", request.getId(),
                    request.getCancelledBy() == null ? null : request.getCancelledBy().getId());
        }
        return events.stream().sorted(Comparator.comparing(AcademicCaseHistoryEvent::occurredAt)
                .thenComparing(AcademicCaseHistoryEvent::type)).toList();
    }

    private static void addEvent(List<AcademicCaseHistoryEvent> events, String type, Instant occurredAt,
            String description, UUID relatedId, UUID actorUserId) {
        if (occurredAt != null) events.add(new AcademicCaseHistoryEvent(type, occurredAt,
                description, relatedId, actorUserId));
    }

    private static String safeName(AppUserEntity user) {
        return user.getFullName() == null || user.getFullName().isBlank() ? user.getEmail() : user.getFullName();
    }

    private static String extension(String filename) {
        int dot = filename == null ? -1 : filename.lastIndexOf('.');
        return dot < 0 || dot == filename.length() - 1 ? null : filename.substring(dot + 1).toLowerCase();
    }

    private static void requireCoordinator(AuthenticatedUser actor) {
        if (actor == null || !actor.enabled() || actor.role() != Role.COORDINATOR) {
            throw new ForbiddenOperationException("Only enabled coordinators can view academic case details");
        }
    }

    public record CoordinatorAcademicCaseDetail(@JsonProperty("case") CoordinatorAcademicCaseInfo caseInfo,
            List<AcademicCaseInputFile> inputFiles, List<AcademicCaseResearcher> researchers,
            List<CoordinatorCaseResult> results, List<AcademicCaseHistoryEvent> history,
            boolean canReviewResults) {}

    public record CoordinatorAcademicCaseInfo(UUID id, String referenceCode, CaseStatus status,
            String studyName, String additionalReference, String originInstitution, LocalDate studyDate,
            String notes, Instant createdAt, Instant updatedAt, UUID availableResultId, Instant receivedAt) {}

    public record AcademicCaseInputFile(UUID id, String originalFilename, String contentType,
            Long sizeBytes, Instant uploadedAt) {}

    public record AcademicCaseResearcher(UUID userId, String fullName, String email,
            ResearcherAuthorizationStatus authorizationStatus, Instant assignedAt) {}

    public record CoordinatorResultFile(UUID id, String originalFilename, String contentType,
            Long sizeBytes, String extension) {}

    public record CoordinatorResultCase(UUID id, String referenceCode, String studyName) {}

    public record AcademicCaseUploader(UUID userId, String studentCode, String fullName) {}

    public record CoordinatorCaseResult(UUID id, CoordinatorResultFile file,
            @JsonProperty("case") CoordinatorResultCase caseInfo,
            AcademicCaseUploader uploadedBy, Instant uploadedAt, Instant updatedAt, int version,
            ResultStatus status, String correctionRequest, com.healthdocs.backend.domain.model.ResultPublicationType publicationType,
            Instant availableAt, Instant deletedAt, String deletionReason) {}

    public record AcademicCaseHistoryEvent(String type, Instant occurredAt, String description,
            UUID relatedId, UUID actorUserId) {}
}
