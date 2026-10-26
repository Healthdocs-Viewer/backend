package com.healthdocs.backend.application.service;

import com.healthdocs.backend.application.port.out.CaseRepositoryPort;
import com.healthdocs.backend.application.port.out.AuthProvider.AuthenticatedUser;
import com.healthdocs.backend.domain.model.MedicalCase;
import com.healthdocs.backend.domain.model.CaseStatus;
import com.healthdocs.backend.domain.model.Role;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.Comparator;
import java.util.Locale;
import java.text.Normalizer;
import org.springframework.stereotype.Service;

@Service
public class ListPatientCasesUseCase {

    private final CaseRepositoryPort cases;

    public ListPatientCasesUseCase(CaseRepositoryPort cases) {
        this.cases = cases;
    }

    public List<CaseView> execute(AuthenticatedUser user) {
        return execute(user, "", "ALL", "createdAt:desc", 0, 100).items();
    }

    public Response execute(AuthenticatedUser user, String q, String status, String sort, int page, int size) {
        if (user == null || !user.enabled()
                || (user.role() != Role.PATIENT && user.role() != Role.RESEARCHER
                && user.role() != Role.COORDINATOR && user.role() != Role.ADMIN)) {
            throw new ForbiddenOperationException("Only enabled users can list their own cases");
        }
        if (page < 0 || size < 1 || size > 100) throw new IllegalArgumentException("Invalid page or size");
        String query = normalize(q);
        String filter = status == null ? "ALL" : status.trim().toUpperCase(Locale.ROOT);
        if (!List.of("ALL", "PENDING", "IN_PROGRESS", "RESULT_AVAILABLE").contains(filter))
            throw new IllegalArgumentException("Unsupported status filter");
        String appliedSort = sort == null || sort.isBlank() ? "createdAt:desc" : sort;
        Comparator<MedicalCase> comparator = switch (appliedSort) {
            case "createdAt:asc" -> Comparator.comparing(MedicalCase::createdAt);
            case "createdAt:desc" -> Comparator.comparing(MedicalCase::createdAt).reversed();
            case "updatedAt:asc" -> Comparator.comparing(MedicalCase::updatedAt);
            case "updatedAt:desc" -> Comparator.comparing(MedicalCase::updatedAt).reversed();
            case "referenceCode:asc" -> Comparator.comparing(MedicalCase::referenceCode);
            case "referenceCode:desc" -> Comparator.comparing(MedicalCase::referenceCode).reversed();
            default -> throw new IllegalArgumentException("Unsupported sort");
        };
        List<MedicalCase> all = cases.findAllByPatientId(user.id());
        List<MedicalCase> matchingQuery = all.stream()
                .filter(c -> query.isEmpty() || normalize(c.referenceCode()).contains(query)
                        || normalize(c.studyName()).contains(query))
                .toList();
        long pending = matchingQuery.stream().filter(c -> isPending(c.status())).count();
        long inProgress = matchingQuery.stream().filter(c -> isInProgress(c.status())).count();
        long available = matchingQuery.stream().filter(c -> c.status() == CaseStatus.RESULT_AVAILABLE).count();
        List<MedicalCase> filtered = matchingQuery.stream()
                .filter(c -> filter.equals("ALL") || filter.equals("PENDING") && isPending(c.status())
                        || filter.equals("IN_PROGRESS") && isInProgress(c.status())
                        || filter.equals("RESULT_AVAILABLE") && c.status() == CaseStatus.RESULT_AVAILABLE)
                .sorted(comparator).toList();
        int from = Math.min(filtered.size(), page * size);
        List<CaseView> items = filtered.subList(from, Math.min(filtered.size(), from + size)).stream()
                .map(c -> CaseView.from(c, cases.findAvailableResultId(c.id()).orElse(null))).toList();
        String appliedQuery = q == null ? "" : q.trim().toLowerCase(Locale.ROOT);
        return new Response(items, page, size, filtered.size(), (int)Math.ceil(filtered.size() / (double)size),
                new StatusCounts(matchingQuery.size(), pending, inProgress, available), new AppliedFilters(appliedQuery, filter, appliedSort));
    }

    private static String normalize(String value) {
        if (value == null) return "";
        return Normalizer.normalize(value.trim(), Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT);
    }

    private static boolean isPending(CaseStatus status) {
        return status == CaseStatus.CONSENT_PENDING || status == CaseStatus.FILE_PENDING
                || status == CaseStatus.CONFIRMATION_PENDING || status == CaseStatus.FILES_RECEIVED
                || status == CaseStatus.INPUT_CHANGE_DRAFT || status == CaseStatus.INPUT_CHANGE_REVIEW;
    }

    private static boolean isInProgress(CaseStatus status) {
        return status == CaseStatus.ENABLED_FOR_PROCESSING || status == CaseStatus.RESULT_UPLOADED;
    }

    public record Response(List<CaseView> items, int page, int size, int totalItems, int totalPages, StatusCounts statusCounts, AppliedFilters appliedFilters) { }
    public record StatusCounts(long ALL, long PENDING, long IN_PROGRESS, long RESULT_AVAILABLE) { }
    public record AppliedFilters(String q, String status, String sort) { }
    public record CaseView(UUID id, String referenceCode, String studyName, com.healthdocs.backend.domain.model.CaseStatus status,
            Instant createdAt, Instant updatedAt, UUID availableResultId) {
        static CaseView from(MedicalCase medicalCase, UUID resultId) {
            return new CaseView(medicalCase.id(), medicalCase.referenceCode(), medicalCase.studyName(), medicalCase.status(),
                    medicalCase.createdAt(), medicalCase.updatedAt(),
                    medicalCase.status() == CaseStatus.RESULT_AVAILABLE ? resultId : null);
        }
    }
}
