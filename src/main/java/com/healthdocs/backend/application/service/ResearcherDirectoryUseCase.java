package com.healthdocs.backend.application.service;

import com.healthdocs.backend.adapter.out.persistence.AppUserRepository;
import com.healthdocs.backend.adapter.out.persistence.entity.AppUserEntity;
import com.healthdocs.backend.application.port.out.AuthProvider.AuthenticatedUser;
import com.healthdocs.backend.domain.model.ResearcherAuthorizationStatus;
import com.healthdocs.backend.domain.model.Role;
import java.text.Normalizer;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class ResearcherDirectoryUseCase {
    private final AppUserRepository users;

    public ResearcherDirectoryUseCase(AppUserRepository users) { this.users = users; }

    public DirectoryResponse execute(AuthenticatedUser requester, String q, ResearcherAuthorizationStatus status,
            String sort, int page, int size) {
        requireManager(requester);
        if (page < 0 || size < 1 || size > 200) throw new IllegalArgumentException("Invalid page or size");
        String normalizedSort = sort == null || sort.isBlank() ? "name,asc" : sort.trim().toLowerCase(Locale.ROOT);
        Comparator<ResearcherView> comparator = switch (normalizedSort) {
            case "name,asc" -> Comparator.comparing(ResearcherView::fullName, Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER));
            case "name,desc" -> Comparator.comparing(ResearcherView::fullName, Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER)).reversed();
            case "code,asc" -> Comparator.comparing(ResearcherView::researcherCode, Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER));
            case "code,desc" -> Comparator.comparing(ResearcherView::researcherCode, Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER)).reversed();
            case "email,asc" -> Comparator.comparing(ResearcherView::email, String.CASE_INSENSITIVE_ORDER);
            case "email,desc" -> Comparator.comparing(ResearcherView::email, String.CASE_INSENSITIVE_ORDER).reversed();
            case "assignedcasecount,asc" -> Comparator.comparingLong(ResearcherView::assignedCaseCount);
            case "assignedcasecount,desc" -> Comparator.comparingLong(ResearcherView::assignedCaseCount).reversed();
            default -> throw new IllegalArgumentException("Unsupported sort; use name, code, email, or assignedCaseCount with asc/desc");
        };
        Map<UUID, Long> assignmentCounts = users.countAssignmentsByUser().stream().collect(Collectors.toMap(
                row -> (UUID) row[0], row -> ((Number) row[1]).longValue()));
        List<AppUserEntity> researchers = users.findByRole(Role.RESEARCHER);
        List<ResearcherView> all = researchers.stream().map(u -> view(u, assignmentCounts)).toList();
        Set<UUID> enabledResearcherIds = researchers.stream().filter(AppUserEntity::isEnabled)
                .map(AppUserEntity::getId).collect(Collectors.toSet());
        String query = normalize(q);
        List<ResearcherView> filtered = all.stream()
                .filter(u -> status == null || u.authorizationStatus() == status)
                // An AUTHORIZED directory query is used to find assignable researchers;
                // disabled accounts cannot receive assignments.
                .filter(u -> status != ResearcherAuthorizationStatus.AUTHORIZED || enabledResearcherIds.contains(u.userId()))
                .filter(u -> query.isEmpty() || contains(u.fullName(), query) || contains(u.researcherCode(), query) || contains(u.email(), query))
                .sorted(comparator.thenComparing(ResearcherView::email, String.CASE_INSENSITIVE_ORDER)).toList();
        int from = (int) Math.min((long) page * size, filtered.size());
        int to = Math.min(from + size, filtered.size());
        int totalPages = (int) Math.ceil((double) filtered.size() / size);
        return new DirectoryResponse(new Summary(researchers.size(),
                users.countResearchersByStatus(Role.RESEARCHER, ResearcherAuthorizationStatus.AUTHORIZED),
                users.countAssignmentsForRole(Role.RESEARCHER)), filtered.subList(from, to), page, size,
                filtered.size(), totalPages, new AppliedFilters(q == null ? "" : q, status, normalizedSort));
    }

    private ResearcherView view(AppUserEntity u, Map<UUID, Long> counts) {
        return new ResearcherView(u.getId(), u.getResearcherCode(), u.getFullName(), u.getEmail(),
                u.getAuthorizationStatus(), counts.getOrDefault(u.getId(), 0L));
    }
    private boolean contains(String value, String q) { return value != null && normalize(value).contains(q); }
    private String normalize(String value) {
        return value == null ? "" : Normalizer.normalize(value.trim(), Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT);
    }
    static void requireManager(AuthenticatedUser requester) {
        if (requester == null || !requester.enabled() ||
                (requester.role() != Role.COORDINATOR && requester.role() != Role.ADMIN)) {
            throw new ForbiddenOperationException("Only enabled coordinators or administrators can manage researchers");
        }
    }

    public record ResearcherView(UUID userId, String researcherCode, String fullName, String email,
            ResearcherAuthorizationStatus authorizationStatus, long assignedCaseCount) {}
    public record Summary(long totalResearchers, long authorizedResearchers, long totalAssignedCases) {}
    public record AppliedFilters(String q, ResearcherAuthorizationStatus status, String sort) {}
    public record DirectoryResponse(Summary summary, List<ResearcherView> items, int page, int size,
            int totalItems, int totalPages, AppliedFilters appliedFilters) {}
}
