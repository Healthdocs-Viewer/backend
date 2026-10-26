package com.healthdocs.backend.application.service;

import com.healthdocs.backend.adapter.out.persistence.CaseResultRepository;
import com.healthdocs.backend.adapter.out.persistence.MedicalCaseRepository;
import com.healthdocs.backend.adapter.out.persistence.entity.CaseResultEntity;
import com.healthdocs.backend.adapter.out.persistence.entity.MedicalCaseEntity;
import com.healthdocs.backend.application.port.out.AuthProvider.AuthenticatedUser;
import com.healthdocs.backend.application.port.out.ActivityEventQueryPort;
import com.healthdocs.backend.domain.model.CaseStatus;
import com.healthdocs.backend.domain.model.Role;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Composes the patient dashboard from case, consent and current-result state. */
@Service
public class PatientDashboardUseCase {
    private static final int RECENT_CASE_LIMIT = 5;
    private static final int NEXT_STEP_LIMIT = 5;
    private static final int AVAILABLE_RESULT_LIMIT = 3;
    private static final int RECENT_ACTIVITY_LIMIT = 5;
    private static final java.util.Set<String> PATIENT_VISIBLE_ACTIVITY_TYPES = java.util.Set.of(
            "CASE_CREATED", "CONSENT_ACCEPTED", "FILE_RECEIVED", "CASE_PROCESSING_ENABLED",
            "RESULT_PUBLISHED", "INPUT_CHANGE_OPENED", "INPUT_CHANGE_SUBMITTED",
            "INPUT_CHANGE_APPROVED", "INPUT_CHANGE_REJECTED", "INPUT_CHANGE_CANCELLED");
    private static final List<CaseStatus> IN_PROGRESS = List.of(
            CaseStatus.ENABLED_FOR_PROCESSING, CaseStatus.RESULT_UPLOADED);

    private final MedicalCaseRepository cases;
    private final CaseResultRepository results;
    private final ActivityEventQueryPort activities;

    public PatientDashboardUseCase(MedicalCaseRepository cases, CaseResultRepository results,
            ActivityEventQueryPort activities) {
        this.cases = cases;
        this.results = results;
        this.activities = activities;
    }

    @Transactional(readOnly = true)
    public Response execute(AuthenticatedUser user) {
        if (user == null || user.role() != Role.PATIENT || !user.enabled()) {
            throw new ForbiddenOperationException("Only enabled patients can view the patient dashboard");
        }
        List<MedicalCaseEntity> allCases = cases.findAllByPatientIdOrderByCreatedAtDesc(user.id());
        List<CaseResultEntity> available = results.findPublishedPatientResults(
                user.id(), null, "", PageRequest.of(0, AVAILABLE_RESULT_LIMIT,
                        Sort.by(Sort.Direction.DESC, "availableAt").and(Sort.by(Sort.Direction.ASC, "id"))))
                .getContent();
        java.util.Set<UUID> availableResultCaseIds = available.stream()
                .map(r -> r.getMedicalCase().getId()).collect(java.util.stream.Collectors.toSet());
        List<NextStep> steps = allCases.stream()
                .map(c -> nextStep(c, availableResultCaseIds.contains(c.getId())))
                .flatMap(java.util.Optional::stream)
                .limit(NEXT_STEP_LIMIT)
                .toList();
        List<RecentActivity> recentActivity = activities.findRecentForPatient(
                user.id(), PATIENT_VISIBLE_ACTIVITY_TYPES, RECENT_ACTIVITY_LIMIT).stream()
                .map(event -> new RecentActivity(event.id(), event.type(), event.occurredAt(),
                        event.caseReferenceCode()))
                .toList();
        return new Response(
                new Summary(allCases.size(), allCases.stream().filter(c -> IN_PROGRESS.contains(c.getStatus())).count(), available.size()),
                allCases.stream().limit(RECENT_CASE_LIMIT).map(PatientDashboardUseCase::recentCase).toList(),
                steps,
                recentActivity,
                available.stream().map(PatientDashboardUseCase::availableResult).toList());
    }

    private static RecentCase recentCase(MedicalCaseEntity c) {
        return new RecentCase(c.getId(), c.getReferenceCode(), c.getStudyName(), c.getStatus(), c.getCreatedAt());
    }

    private static java.util.Optional<NextStep> nextStep(MedicalCaseEntity c, boolean resultAvailable) {
        String type;
        String title;
        String description;
        String label;
        String priority;
        if (c.getStatus() == CaseStatus.CONSENT_PENDING) {
            type = "ACCEPT_CONSENT"; title = "Completar consentimiento";
            description = "Revisa y acepta los términos para continuar con el estudio.";
            label = "Completar"; priority = "HIGH";
        } else if (c.getStatus() == CaseStatus.FILE_PENDING) {
            type = "UPLOAD_FILE"; title = "Cargar archivo pendiente";
            description = "Adjunta las imágenes o documentos solicitados por tu centro médico.";
            label = "Cargar"; priority = "NORMAL";
        } else if (c.getStatus() == CaseStatus.CONFIRMATION_PENDING) {
            type = "CONFIRM_CASE"; title = "Revisar y enviar el caso";
            description = "Revisa los archivos adjuntos y confirma el envío del caso.";
            label = "Revisar"; priority = "NORMAL";
        } else if (c.getStatus() == CaseStatus.RESULT_AVAILABLE && resultAvailable) {
            type = "VIEW_RESULT"; title = "Consultar resultado disponible";
            description = "El resultado de tu estudio ya está disponible.";
            label = "Ver resultado"; priority = "NORMAL";
        } else {
            return java.util.Optional.empty();
        }
        UUID id = UUID.nameUUIDFromBytes((c.getId() + ":" + type).getBytes(StandardCharsets.UTF_8));
        return java.util.Optional.of(new NextStep(id, c.getId(), c.getReferenceCode(), type, title,
                description, priority, c.getCreatedAt(), new Action(type, label)));
    }

    private static AvailableResult availableResult(CaseResultEntity r) {
        MedicalCaseEntity c = r.getMedicalCase();
        return new AvailableResult(r.getId(), c.getId(), c.getReferenceCode(), c.getStudyName(), r.getAvailableAt());
    }

    public record Response(Summary summary, List<RecentCase> recentCases, List<NextStep> nextSteps,
            List<RecentActivity> recentActivity,
            List<AvailableResult> availableResults) { }
    public record Summary(long activeCases, long inProgressCases, long availableResults) { }
    public record RecentCase(UUID id, String referenceCode, String studyName, CaseStatus status, Instant createdAt) { }
    public record NextStep(UUID id, UUID caseId, String caseReferenceCode, String type, String title,
            String description, String priority, Instant createdAt, Action action) { }
    public record Action(String type, String label) { }
    public record RecentActivity(UUID id, String type, Instant occurredAt, String caseReferenceCode) { }
    public record AvailableResult(UUID id, UUID caseId, String caseReferenceCode, String studyName, Instant availableAt) { }
}
