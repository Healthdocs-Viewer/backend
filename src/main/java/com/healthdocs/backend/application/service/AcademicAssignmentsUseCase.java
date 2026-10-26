package com.healthdocs.backend.application.service;

import com.healthdocs.backend.adapter.out.persistence.AppUserRepository;
import com.healthdocs.backend.adapter.out.persistence.CaseAssignmentRepository;
import com.healthdocs.backend.adapter.out.persistence.MedicalCaseRepository;
import com.healthdocs.backend.adapter.out.persistence.StoredFileRepository;
import com.healthdocs.backend.adapter.out.persistence.entity.AppUserEntity;
import com.healthdocs.backend.adapter.out.persistence.entity.CaseAssignmentEntity;
import com.healthdocs.backend.adapter.out.persistence.entity.MedicalCaseEntity;
import com.healthdocs.backend.application.port.out.AuthProvider.AuthenticatedUser;
import com.healthdocs.backend.domain.model.CaseStatus;
import com.healthdocs.backend.domain.model.ResearcherAuthorizationStatus;
import com.healthdocs.backend.domain.model.Role;
import java.text.Normalizer;
import java.util.*;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

/** Academic assignment projections and set-based assignment updates. Assignment membership remains in case_assignment. */
@Service
public class AcademicAssignmentsUseCase {
    private static final List<CaseStatus> ACADEMIC_STATUSES = List.of(
            CaseStatus.ENABLED_FOR_PROCESSING, CaseStatus.RESULT_UPLOADED, CaseStatus.RESULT_AVAILABLE);
    private static final List<CaseStatus> ACADEMIC_VISIBLE_STATUSES = List.of(
            CaseStatus.FILES_RECEIVED, CaseStatus.ENABLED_FOR_PROCESSING, CaseStatus.INPUT_CHANGE_DRAFT,
            CaseStatus.INPUT_CHANGE_REVIEW, CaseStatus.RESULT_UPLOADED, CaseStatus.RESULT_AVAILABLE);
    private final MedicalCaseRepository cases;
    private final CaseAssignmentRepository assignments;
    private final AppUserRepository users;
    private final StoredFileRepository files;
    private com.healthdocs.backend.application.port.out.ActivityEventPort activityEvents;

    public AcademicAssignmentsUseCase(MedicalCaseRepository cases, CaseAssignmentRepository assignments,
            AppUserRepository users, StoredFileRepository files) {
        this.cases = cases; this.assignments = assignments; this.users = users; this.files = files;
    }

    @Autowired
    public void setActivityEventRecorder(
            com.healthdocs.backend.application.port.out.ActivityEventPort activityEvents) {
        this.activityEvents = activityEvents;
    }

    @Transactional(readOnly = true)
    public CaseAssignmentsView forCase(AuthenticatedUser actor, UUID caseId) {
        requireManager(actor);
        cases.findById(caseId).orElseThrow(() -> new ResourceNotFoundException("Case not found"));
        List<ResearcherView> researchers = assignments.findAllByCaseId(caseId).stream()
                .map(a -> users.findById(a.getUserId()).orElse(null))
                .filter(Objects::nonNull).filter(u -> u.getRole() == Role.RESEARCHER)
                .map(this::view).sorted(Comparator.comparing(ResearcherView::fullName,
                        Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER))).toList();
        return new CaseAssignmentsView(caseId, researchers, researchers.stream().map(ResearcherView::userId).toList());
    }

    @Transactional
    public CaseAssignmentsView replaceCaseResearchers(AuthenticatedUser actor, UUID caseId, List<UUID> researcherIds) {
        requireManager(actor);
        MedicalCaseEntity medicalCase = cases.findByIdForUpdate(caseId).orElseThrow(() -> new ResourceNotFoundException("Case not found"));
        requireAssignableState(medicalCase);
        List<UUID> requested = distinct(researcherIds);
        Map<UUID, AppUserEntity> requestedUsers = loadResearchers(requested);
        List<CaseAssignmentEntity> current = assignments.findAllByCaseId(caseId);
        ensureCoordinatorAssignment(actor, caseId);
        Set<UUID> currentIds = current.stream().filter(a -> isResearcher(a.getUserId()))
                .map(CaseAssignmentEntity::getUserId).collect(Collectors.toSet());
        // D-01: a restricted researcher keeps current assignments and access; restriction only blocks additions.
        Set<UUID> retainedRestricted = currentIds.stream().filter(id -> {
            AppUserEntity u = users.findById(id).orElse(null);
            return u != null && u.getAuthorizationStatus() == ResearcherAuthorizationStatus.RESTRICTED;
        }).collect(Collectors.toSet());
        for (UUID id : requested) {
            AppUserEntity user = requestedUsers.get(id);
            if (!currentIds.contains(id) && user.getAuthorizationStatus() != ResearcherAuthorizationStatus.AUTHORIZED)
                throw new BusinessRuleViolationException("Restricted researchers cannot receive new assignments");
        }
        Set<UUID> desired = new LinkedHashSet<>(requested); desired.addAll(retainedRestricted);
        java.time.Instant now = java.time.Instant.now();
        for (UUID id : currentIds) if (!desired.contains(id)) {
            assignments.deleteByCaseIdAndUserId(caseId, id);
            if (activityEvents != null) activityEvents.record("RESEARCHER_UNASSIGNED", now, caseId, actor.id(), id,
                    "USER", id, "RESEARCHER_UNASSIGNED:" + caseId + ":" + id + ":" + UUID.randomUUID());
        }
        for (UUID id : desired) if (!currentIds.contains(id)) {
            assignments.save(new CaseAssignmentEntity(caseId, id));
            if (activityEvents != null) activityEvents.record("RESEARCHER_ASSIGNED", now, caseId, actor.id(), id,
                    "USER", id, "RESEARCHER_ASSIGNED:" + caseId + ":" + id + ":" + UUID.randomUUID());
        }
        boolean processingEnabled = medicalCase.getStatus() == CaseStatus.FILES_RECEIVED && !desired.isEmpty();
        if (processingEnabled) {
            medicalCase.setStatus(CaseStatus.ENABLED_FOR_PROCESSING);
            medicalCase.setUpdatedAt(now);
            cases.save(medicalCase);
        } else if (medicalCase.getStatus() == CaseStatus.ENABLED_FOR_PROCESSING && desired.isEmpty()) {
            medicalCase.setStatus(CaseStatus.FILES_RECEIVED);
            medicalCase.setUpdatedAt(now);
            cases.save(medicalCase);
        }
        if (processingEnabled && activityEvents != null) activityEvents.record("CASE_PROCESSING_ENABLED", now,
                caseId, actor.id(), medicalCase.getPatient().getId(), "CASE", caseId,
                "CASE_PROCESSING_ENABLED:" + caseId + ":" + UUID.randomUUID());
        return forCase(actor, caseId);
    }

    @Transactional(readOnly = true)
    public AcademicCasesResponse casesForResearcher(AuthenticatedUser actor, UUID researcherId,
            String q, String sort, int page, int size) {
        requireManager(actor);
        researcher(researcherId);
        validatePage(page, size);
        String normalizedSort = normalizeSort(sort);
        String query = normalize(q);
        List<AcademicCaseView> filtered = cases.findAcademicCases(ACADEMIC_VISIBLE_STATUSES).stream()
                .filter(c -> files.countByMedicalCaseIdAndFileKindAndInputActiveTrue(c.getId(), com.healthdocs.backend.domain.model.FileKind.INPUT) > 0)
                .map(c -> caseView(c, assignments.existsByCaseIdAndUserId(c.getId(), researcherId)))
                .filter(c -> query.isEmpty() || normalize(c.referenceCode()).contains(query) || normalize(c.studyName()).contains(query))
                .sorted(caseComparator(normalizedSort)).toList();
        int from = (int)Math.min((long)page * size, filtered.size()), to = Math.min(from + size, filtered.size());
        return new AcademicCasesResponse(filtered.subList(from, to), page, size, filtered.size(),
                (int)Math.ceil((double)filtered.size()/size), new AppliedFilters(q == null ? "" : q, normalizedSort));
    }

    /** Coordinator listing for /casos_academicos. */
    @Transactional(readOnly = true)
    public CoordinatorAcademicCasesResponse coordinatorCases(AuthenticatedUser actor, String q, CaseStatus status,
            String sort, int page, int size) {
        requireManager(actor);
        validatePage(page, size);
        String normalizedSort = normalizeSort(sort);
        String query = normalize(q);
        List<CoordinatorAcademicCaseView> filtered = cases.findAcademicCases(ACADEMIC_VISIBLE_STATUSES).stream()
                .filter(c -> files.countByMedicalCaseIdAndFileKindAndInputActiveTrue(c.getId(),
                        com.healthdocs.backend.domain.model.FileKind.INPUT) > 0)
                .filter(c -> status == null || c.getStatus() == status)
                .map(c -> new CoordinatorAcademicCaseView(c.getId(), c.getReferenceCode(), c.getStudyName(),
                        c.getStatus(), c.getReceivedAt(), c.getUpdatedAt()))
                .filter(c -> query.isEmpty() || normalize(c.referenceCode()).contains(query)
                        || normalize(c.studyName()).contains(query))
                .sorted(coordinatorCaseComparator(normalizedSort)).toList();
        int from = (int)Math.min((long)page * size, filtered.size()), to = Math.min(from + size, filtered.size());
        return new CoordinatorAcademicCasesResponse(filtered.subList(from, to), page, size, filtered.size(),
                (int)Math.ceil((double)filtered.size()/size), new AppliedFilters(q == null ? "" : q, normalizedSort));
    }

    @Transactional
    public void replaceResearcherCases(AuthenticatedUser actor, UUID researcherId, List<UUID> caseIds) {
        requireManager(actor);
        AppUserEntity target = researcher(researcherId);
        List<UUID> requested = distinct(caseIds);
        List<CaseAssignmentEntity> current = assignments.findAllByUserId(researcherId);
        Set<UUID> currentIds = current.stream().map(CaseAssignmentEntity::getCaseId).collect(Collectors.toSet());
        if (target.getAuthorizationStatus() != ResearcherAuthorizationStatus.AUTHORIZED &&
                requested.stream().anyMatch(id -> !currentIds.contains(id)))
            throw new BusinessRuleViolationException("Restricted researchers cannot receive new assignments");
        Map<UUID, MedicalCaseEntity> requestedCases = new HashMap<>();
        for (UUID id : requested) {
            MedicalCaseEntity c = cases.findByIdForUpdate(id).orElseThrow(() -> new ResourceNotFoundException("Case not found"));
            requireAssignableState(c); requestedCases.put(id, c);
        }
        for (UUID id : requested) ensureCoordinatorAssignment(actor, id);
        if (target.getAuthorizationStatus() == ResearcherAuthorizationStatus.RESTRICTED) return;
        java.time.Instant now = java.time.Instant.now();
        for (UUID id : currentIds) if (!requested.contains(id)) {
            MedicalCaseEntity currentCase = cases.findByIdForUpdate(id)
                    .orElseThrow(() -> new ResourceNotFoundException("Case not found"));
            if (currentCase.getStatus() != CaseStatus.INPUT_CHANGE_DRAFT
                    && currentCase.getStatus() != CaseStatus.INPUT_CHANGE_REVIEW) {
                assignments.deleteByCaseIdAndUserId(id, researcherId);
                if (activityEvents != null) activityEvents.record("RESEARCHER_UNASSIGNED", now, id, actor.id(),
                        researcherId, "USER", researcherId,
                        "RESEARCHER_UNASSIGNED:" + id + ":" + researcherId + ":" + UUID.randomUUID());
            }
        }
        for (UUID id : requested) if (!currentIds.contains(id)) {
            assignments.save(new CaseAssignmentEntity(id, researcherId));
            MedicalCaseEntity medicalCase = requestedCases.get(id);
            if (activityEvents != null) activityEvents.record("RESEARCHER_ASSIGNED", now, id, actor.id(),
                    researcherId, "USER", researcherId,
                    "RESEARCHER_ASSIGNED:" + id + ":" + researcherId + ":" + UUID.randomUUID());
            if (medicalCase.getStatus() == CaseStatus.FILES_RECEIVED) {
                medicalCase.setStatus(CaseStatus.ENABLED_FOR_PROCESSING);
                medicalCase.setUpdatedAt(now);
                cases.save(medicalCase);
                if (activityEvents != null) activityEvents.record("CASE_PROCESSING_ENABLED", now, id, actor.id(),
                        medicalCase.getPatient().getId(), "CASE", id,
                        "CASE_PROCESSING_ENABLED:" + id + ":" + UUID.randomUUID());
            }
        }
    }

    private Map<UUID, AppUserEntity> loadResearchers(List<UUID> ids) {
        Map<UUID, AppUserEntity> result = new HashMap<>();
        for (UUID id : ids) result.put(id, researcher(id));
        return result;
    }
    private AppUserEntity researcher(UUID id) {
        AppUserEntity u = users.findById(id).orElseThrow(() -> new ResourceNotFoundException("Researcher not found"));
        if (u.getRole() != Role.RESEARCHER) throw new BusinessRuleViolationException("User is not a researcher");
        if (!u.isEnabled()) throw new BusinessRuleViolationException("Researcher account is disabled");
        return u;
    }
    private boolean isResearcher(UUID id) { return users.findById(id).map(u -> u.getRole() == Role.RESEARCHER).orElse(false); }
    private void ensureCoordinatorAssignment(AuthenticatedUser actor, UUID caseId) {
        if (actor.role() == Role.COORDINATOR && !assignments.existsByCaseIdAndUserId(caseId, actor.id())) {
            assignments.save(new CaseAssignmentEntity(caseId, actor.id()));
        }
    }
    private ResearcherView view(AppUserEntity u) { return new ResearcherView(u.getId(), u.getResearcherCode(), u.getFullName(), u.getEmail(), u.getAuthorizationStatus(), users.countAssignmentsByUserId(u.getId())); }
    private AcademicCaseView caseView(MedicalCaseEntity c, boolean assigned) {
        return new AcademicCaseView(c.getId(), c.getReferenceCode(), c.getStudyName(), c.getStatus(),
                c.getReceivedAt(), c.getUpdatedAt(), assigned);
    }
    private void requireAssignableState(MedicalCaseEntity c) {
        if (c.getStatus() == CaseStatus.INPUT_CHANGE_DRAFT
                || c.getStatus() == CaseStatus.INPUT_CHANGE_REVIEW)
            throw new BusinessRuleViolationException("Assignments are blocked while an input-change request is open");
        if (!ACADEMIC_STATUSES.contains(c.getStatus()) && c.getStatus() != CaseStatus.FILES_RECEIVED)
            throw new BusinessRuleViolationException("Case is not in an assignable academic state");
        if (files.countByMedicalCaseIdAndFileKindAndInputActiveTrue(c.getId(), com.healthdocs.backend.domain.model.FileKind.INPUT) == 0)
            throw new BusinessRuleViolationException("Case has no input file");
    }
    private static void requireManager(AuthenticatedUser a) {
        ResearcherDirectoryUseCase.requireManager(a);
    }
    private static void validatePage(int page, int size) { if(page < 0 || size < 1 || size > 200) throw new IllegalArgumentException("Invalid page or size"); }
    private static List<UUID> distinct(List<UUID> values) { if(values == null) throw new IllegalArgumentException("IDs are required"); return new ArrayList<>(new LinkedHashSet<>(values)); }
    private static String normalize(String s) { return s == null ? "" : Normalizer.normalize(s.trim(), Normalizer.Form.NFD).replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT); }
    private static String normalizeSort(String s) { String value = s == null || s.isBlank() ? "receivedAt,desc" : s.trim(); if (!Set.of("receivedAt,asc","receivedAt,desc","referenceCode,asc","referenceCode,desc","status,asc","status,desc").contains(value)) throw new IllegalArgumentException("Unsupported sort"); return value; }
    private static Comparator<AcademicCaseView> caseComparator(String sort) {
        Comparator<AcademicCaseView> c = switch(sort) {
            case "referenceCode,asc" -> Comparator.comparing(AcademicCaseView::referenceCode, String.CASE_INSENSITIVE_ORDER);
            case "referenceCode,desc" -> Comparator.comparing(AcademicCaseView::referenceCode, String.CASE_INSENSITIVE_ORDER).reversed();
            case "status,asc" -> Comparator.comparing((AcademicCaseView x) -> x.status().name());
            case "status,desc" -> Comparator.comparing((AcademicCaseView x) -> x.status().name()).reversed();
            case "receivedAt,asc" -> Comparator.comparing(AcademicCaseView::receivedAt, Comparator.nullsLast(Comparator.naturalOrder()));
            default -> Comparator.comparing(AcademicCaseView::receivedAt, Comparator.nullsLast(Comparator.reverseOrder()));
        };
        return c.thenComparing(AcademicCaseView::referenceCode, String.CASE_INSENSITIVE_ORDER);
    }
    private static Comparator<CoordinatorAcademicCaseView> coordinatorCaseComparator(String sort) {
        Comparator<CoordinatorAcademicCaseView> c = switch(sort) {
            case "referenceCode,asc" -> Comparator.comparing(CoordinatorAcademicCaseView::referenceCode, String.CASE_INSENSITIVE_ORDER);
            case "referenceCode,desc" -> Comparator.comparing(CoordinatorAcademicCaseView::referenceCode, String.CASE_INSENSITIVE_ORDER).reversed();
            case "status,asc" -> Comparator.comparing((CoordinatorAcademicCaseView x) -> x.status().name());
            case "status,desc" -> Comparator.comparing((CoordinatorAcademicCaseView x) -> x.status().name()).reversed();
            case "receivedAt,asc" -> Comparator.comparing(CoordinatorAcademicCaseView::receivedAt, Comparator.nullsLast(Comparator.naturalOrder()));
            default -> Comparator.comparing(CoordinatorAcademicCaseView::receivedAt, Comparator.nullsLast(Comparator.reverseOrder()));
        };
        return c.thenComparing(CoordinatorAcademicCaseView::referenceCode, String.CASE_INSENSITIVE_ORDER);
    }
    public record ResearcherView(UUID userId, String researcherCode, String fullName, String email, ResearcherAuthorizationStatus authorizationStatus, long assignedCaseCount) {}
    public record CaseAssignmentsView(UUID caseId, List<ResearcherView> researchers, List<UUID> researcherIds) {}
    public record AcademicCaseView(UUID id, String referenceCode, String studyName, CaseStatus status,
            java.time.Instant receivedAt, java.time.Instant updatedAt, boolean isAssigned) {}
    public record CoordinatorAcademicCaseView(UUID id, String referenceCode, String studyName, CaseStatus status,
            java.time.Instant receivedAt, java.time.Instant updatedAt) {}
    public record AppliedFilters(String q, String sort) {}
    public record AcademicCasesResponse(List<AcademicCaseView> items, int page, int size, int totalItems, int totalPages, AppliedFilters appliedFilters) {}
    public record CoordinatorAcademicCasesResponse(List<CoordinatorAcademicCaseView> items, int page, int size,
            int totalItems, int totalPages, AppliedFilters appliedFilters) {}
}
