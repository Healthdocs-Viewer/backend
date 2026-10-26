package com.healthdocs.backend.application.service;

import com.healthdocs.backend.application.port.out.ActivityEventQueryPort;
import com.healthdocs.backend.application.port.out.AuthProvider.AuthenticatedUser;
import com.healthdocs.backend.domain.model.Role;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CaseActivityQueryService {
    private static final Set<String> PATIENT_VISIBLE_TYPES = Set.of(
            "CASE_CREATED", "CONSENT_ACCEPTED", "FILE_RECEIVED", "CASE_PROCESSING_ENABLED",
            "RESULT_PUBLISHED", "INPUT_CHANGE_OPENED", "INPUT_CHANGE_SUBMITTED",
            "INPUT_CHANGE_APPROVED", "INPUT_CHANGE_REJECTED", "INPUT_CHANGE_CANCELLED");

    private final ActivityEventQueryPort events;
    private final ResourceAuthorization authorization;

    public CaseActivityQueryService(ActivityEventQueryPort events, ResourceAuthorization authorization) {
        this.events = events;
        this.authorization = authorization;
    }

    @Transactional(readOnly = true)
    public Page execute(AuthenticatedUser user, UUID caseId, int page, int size) {
        if (page < 0) throw new IllegalArgumentException("page must be zero or greater");
        if (size < 1 || size > 100) throw new IllegalArgumentException("size must be between 1 and 100");
        if (user == null || (!isSupportedRole(user.role())) || !authorization.canAccessCase(user, caseId)) {
            throw new ForbiddenOperationException("Case is not available");
        }
        Set<String> visibleTypes = user.role() == Role.PATIENT ? PATIENT_VISIBLE_TYPES : null;
        long total = events.countByCase(caseId, visibleTypes);
        long offset = (long) page * size;
        List<Activity> items = events.findByCase(caseId, visibleTypes, size, offset).stream()
                .map(event -> new Activity(event.id(), event.type(), event.occurredAt())).toList();
        int totalPages = total == 0 ? 0 : (int) Math.ceil((double) total / size);
        return new Page(items, page, size, total, totalPages);
    }

    private static boolean isSupportedRole(Role role) {
        return role == Role.PATIENT || role == Role.RESEARCHER || role == Role.COORDINATOR;
    }

    public record Activity(UUID id, String type, java.time.Instant occurredAt) { }
    public record Page(List<Activity> items, int page, int size, long totalElements, int totalPages) { }
}
