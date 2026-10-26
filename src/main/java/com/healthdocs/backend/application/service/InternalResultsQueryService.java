package com.healthdocs.backend.application.service;

import com.healthdocs.backend.adapter.out.persistence.AppUserRepository;
import com.healthdocs.backend.adapter.out.persistence.CaseAssignmentRepository;
import com.healthdocs.backend.adapter.out.persistence.CaseResultRepository;
import com.healthdocs.backend.adapter.out.persistence.MedicalCaseRepository;
import com.healthdocs.backend.adapter.out.persistence.entity.AppUserEntity;
import com.healthdocs.backend.adapter.out.persistence.entity.CaseResultEntity;
import com.healthdocs.backend.application.port.out.AuthProvider.AuthenticatedUser;
import com.healthdocs.backend.domain.model.ResultPublicationType;
import com.healthdocs.backend.domain.model.ResultStatus;
import com.healthdocs.backend.domain.model.Role;
import java.util.*;
import java.util.stream.Collectors;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.fasterxml.jackson.annotation.JsonProperty;

/** Read projections for the assigned internal result workflow. */
@Service
public class InternalResultsQueryService {
    private final CaseResultRepository results;
    private final CaseAssignmentRepository assignments;
    private final MedicalCaseRepository cases;
    private final AppUserRepository users;

    public InternalResultsQueryService(CaseResultRepository results, CaseAssignmentRepository assignments,
            MedicalCaseRepository cases, AppUserRepository users) {
        this.results = results;
        this.assignments = assignments;
        this.cases = cases;
        this.users = users;
    }

    @Transactional(readOnly = true)
    public ResearcherResults researcher(AuthenticatedUser user, String q, ResultStatus status, String sort, int page, int size) {
        requireInternal(user);
        if (user.role() != Role.RESEARCHER) throw new ForbiddenOperationException("Researcher access required");
        Query query = query(q, status, sort, page, size);
        Page<CaseResultEntity> found = results.findVisibleResults(user.id(), false, status, query.normalizedQ,
                PageRequest.of(page, size, query.sort));
        Map<ResultStatus, Long> counts = counts(user.id(), false, status, query.normalizedQ);
        return new ResearcherResults(found.getContent().stream().map(r -> researcherItem(r, version(r))).toList(),
                new Summary(total(counts), count(counts, ResultStatus.UPLOADED), count(counts, ResultStatus.AVAILABLE), count(counts, ResultStatus.CORRECTION_REQUIRED)),
                page, size, found.getTotalElements(), found.getTotalPages(), new AppliedFilters(query.normalizedQ, status, query.sortString));
    }

    @Transactional(readOnly = true)
    public CoordinatorResults coordinator(AuthenticatedUser user, String q, ResultStatus status, String sort, int page, int size) {
        requireInternal(user);
        if (user.role() != Role.COORDINATOR) throw new ForbiddenOperationException("Coordinator access required");
        Query query = query(q, status, sort, page, size);
        Page<CaseResultEntity> found = results.findVisibleResults(user.id(), true, status, query.normalizedQ,
                PageRequest.of(page, size, query.sort));
        Map<ResultStatus, Long> counts = counts(user.id(), true, status, query.normalizedQ);
        Set<UUID> uploaderIds = found.getContent().stream().map(CaseResultEntity::getUploadedBy).filter(Objects::nonNull).collect(Collectors.toSet());
        Map<UUID, AppUserEntity> uploaderMap = users.findAllById(uploaderIds).stream().collect(Collectors.toMap(AppUserEntity::getId, u -> u));
        return new CoordinatorResults(found.getContent().stream().map(r -> coordinatorItem(r, version(r), uploaderMap.get(r.getUploadedBy()))).toList(),
                new Summary(total(counts), count(counts, ResultStatus.UPLOADED), count(counts, ResultStatus.AVAILABLE), count(counts, ResultStatus.CORRECTION_REQUIRED)),
                page, size, found.getTotalElements(), found.getTotalPages(), new AppliedFilters(query.normalizedQ, status, query.sortString));
    }

    /** Coordinators can inspect any case; researchers can inspect assigned cases. */
    @Transactional(readOnly = true)
    public History history(AuthenticatedUser user, UUID caseId, int page, int size) {
        requireInternal(user);
        if (page < 0 || size < 1 || size > 100) throw new IllegalArgumentException("page must be non-negative and size must be between 1 and 100");
        if (user.role() != Role.COORDINATOR && !assignments.existsByCaseIdAndUserId(caseId, user.id())) throw new ForbiddenOperationException("Case is not available");
        if (!cases.existsById(caseId)) throw new ResourceNotFoundException("Case not found");
        Page<CaseResultEntity> found = results.findHistory(caseId, PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt")));
        Set<UUID> uploaderIds = found.getContent().stream().map(CaseResultEntity::getUploadedBy).filter(Objects::nonNull).collect(Collectors.toSet());
        Map<UUID, AppUserEntity> uploaderMap = users.findAllById(uploaderIds).stream().collect(Collectors.toMap(AppUserEntity::getId, u -> u));
        List<CoordinatorItem> items = found.getContent().stream().map(r -> coordinatorItem(r, version(r), uploaderMap.get(r.getUploadedBy()))).toList();
        return new History(items, page, size, found.getTotalElements(), found.getTotalPages());
    }

    private Map<ResultStatus, Long> counts(UUID userId, boolean coordinator, ResultStatus status, String q) {
        Map<ResultStatus, Long> counts = new EnumMap<>(ResultStatus.class);
        for (Object[] row : results.summarizeVisibleResults(userId, coordinator, status, q)) counts.put((ResultStatus) row[0], (Long) row[1]);
        return counts;
    }
    private int version(CaseResultEntity r) { return r.getVersionNumber() != null ? r.getVersionNumber() : results.findMaximumVersionNumber(r.getMedicalCase().getId()) + 1; }
    private ResearcherItem researcherItem(CaseResultEntity r, int version) {
        var f = r.getFile(); var c = r.getMedicalCase();
        return new ResearcherItem(r.getId(), f.getOriginalFilename(), f.getContentType(), f.getSizeBytes(),
                new CaseInfo(c.getId(), c.getReferenceCode(), c.getStudyName()), r.getStatus(), r.getUploadedAt(), r.getUpdatedAt(), version,
                r.getPublicationType(), correction(r), r.getAvailableAt());
    }
    private CoordinatorItem coordinatorItem(CaseResultEntity r, int version, AppUserEntity uploader) {
        var f = r.getFile(); var c = r.getMedicalCase();
        Uploader profile = uploader == null ? null : new Uploader(uploader.getId(), uploader.getResearcherCode(), uploader.getFullName());
        return new CoordinatorItem(r.getId(), new FileInfo(f.getId(), f.getOriginalFilename(), f.getContentType(), f.getSizeBytes(), extension(f.getOriginalFilename())),
                new CaseInfo(c.getId(), c.getReferenceCode(), c.getStudyName()), profile, r.getUploadedAt(), r.getUpdatedAt(), version,
                r.getStatus(), correction(r), r.getPublicationType(), r.getAvailableAt(), r.getDeletedAt(), r.getDeletionReason());
    }
    private static String correction(CaseResultEntity r) { return r.getStatus() == ResultStatus.CORRECTION_REQUIRED ? r.getCorrectionRequest() : null; }
    private static String extension(String filename) { int dot = filename == null ? -1 : filename.lastIndexOf('.'); return dot < 0 || dot == filename.length() - 1 ? null : filename.substring(dot + 1).toLowerCase(Locale.ROOT); }
    private static long count(Map<ResultStatus, Long> m, ResultStatus status) { return m.getOrDefault(status, 0L); }
    private static long total(Map<ResultStatus, Long> m) { return m.values().stream().mapToLong(Long::longValue).sum(); }
    private static void requireInternal(AuthenticatedUser u) { if (u == null || !u.enabled() || (u.role() != Role.RESEARCHER && u.role() != Role.COORDINATOR)) throw new ForbiddenOperationException("Internal access required"); }

    private static Query query(String q, ResultStatus status, String sortValue, int page, int size) {
        if (page < 0 || size < 1 || size > 100) throw new IllegalArgumentException("page must be non-negative and size must be between 1 and 100");
        if (status == ResultStatus.DELETED) throw new IllegalArgumentException("Deleted results are available only in authorized history");
        String normalizedQ = q == null ? "" : q.trim().toLowerCase(Locale.ROOT);
        String normalizedSort = sortValue == null || sortValue.isBlank() ? "uploadedAt,desc" : sortValue.trim();
        String[] parts = normalizedSort.split(",", -1);
        if (parts.length != 2) throw new IllegalArgumentException("sort must use field,asc|desc");
        String property = switch (parts[0]) {
            case "uploadedAt" -> "uploadedAt"; case "updatedAt" -> "updatedAt"; case "availableAt" -> "availableAt";
            case "version" -> "versionNumber"; case "originalFilename" -> "file.originalFilename";
            case "caseReferenceCode" -> "medicalCase.referenceCode"; case "status" -> "status";
            default -> throw new IllegalArgumentException("Unsupported result sort field");
        };
        Sort.Direction direction;
        try { direction = Sort.Direction.fromString(parts[1]); } catch (IllegalArgumentException ex) { throw new IllegalArgumentException("sort direction must be asc or desc", ex); }
        Sort sort = Sort.by(direction, property).and(Sort.by(Sort.Direction.ASC, "id"));
        return new Query(normalizedQ, normalizedSort, sort);
    }
    private record Query(String normalizedQ, String sortString, Sort sort) {}
    public record CaseInfo(UUID id, String referenceCode, String studyName) {}
    public record Uploader(UUID userId, String studentCode, String fullName) {}
    public record FileInfo(UUID id, String originalFilename, String contentType, Long sizeBytes, String extension) {}
    public record Summary(long total, long uploaded, long available, long correctionRequired) {}
    public record AppliedFilters(String q, ResultStatus status, String sort) {}
    public record ResearcherItem(UUID id, String originalFilename, String contentType, Long sizeBytes, @JsonProperty("case") CaseInfo caseInfo,
            ResultStatus status, java.time.Instant uploadedAt, java.time.Instant updatedAt, int version,
            ResultPublicationType publicationType, String correctionRequest, java.time.Instant availableAt) {
        public CaseInfo getCase() { return caseInfo; }
    }
    public record CoordinatorItem(UUID id, FileInfo file, @JsonProperty("case") CaseInfo caseInfo, Uploader uploadedBy, java.time.Instant uploadedAt,
            java.time.Instant updatedAt, int version, ResultStatus status, String correctionRequest,
            ResultPublicationType publicationType, java.time.Instant availableAt, java.time.Instant deletedAt, String deletionReason) {
        public CaseInfo getCase() { return caseInfo; }
    }
    public record ResearcherResults(List<ResearcherItem> items, Summary summary, int page, int size, long totalItems, int totalPages, AppliedFilters appliedFilters) {}
    public record CoordinatorResults(List<CoordinatorItem> items, Summary summary, int page, int size, long totalItems, int totalPages, AppliedFilters appliedFilters) {}
    public record History(List<CoordinatorItem> items, int page, int size, long totalItems, int totalPages) {}
}
