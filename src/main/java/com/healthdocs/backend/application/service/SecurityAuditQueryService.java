package com.healthdocs.backend.application.service;

import com.healthdocs.backend.application.port.out.AuthProvider.AuthenticatedUser;
import com.healthdocs.backend.application.port.out.SecurityAuditEventQueryPort;
import com.healthdocs.backend.domain.model.Role;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SecurityAuditQueryService {
    private static final Set<String> TYPES = Set.of("ACCOUNT_CREATED", "ACCOUNT_ENABLED", "ACCOUNT_DISABLED",
            "ACCOUNT_ROLE_CHANGED", "RESEARCHER_AUTHORIZATION_CHANGED", "ACCOUNT_PROFILE_UPDATED",
            "PASSWORD_CHANGED", "SENSITIVE_RESOURCE_ACCESS_GRANTED");
    private static final Set<String> RESOURCE_TYPES = Set.of("INPUT_FILE", "RESULT");
    private static final int MAX_SIZE = 100;

    private final SecurityAuditEventQueryPort events;

    public SecurityAuditQueryService(SecurityAuditEventQueryPort events) {
        this.events = events;
    }

    @Transactional(readOnly = true)
    public Page execute(AuthenticatedUser requester, int page, int size, String type, UUID actorUserId,
            UUID subjectUserId, String resourceType, UUID resourceId) {
        if (requester == null || !requester.enabled() || requester.role() != Role.ADMIN) {
            throw new ForbiddenOperationException("Security audit access is restricted to enabled administrators");
        }
        if (page < 0) throw new IllegalArgumentException("page must be zero or greater");
        if (size < 1 || size > MAX_SIZE) throw new IllegalArgumentException("size must be between 1 and 100");
        if (type != null && !TYPES.contains(type)) throw new IllegalArgumentException("Unsupported audit event type");
        if (resourceType != null && !RESOURCE_TYPES.contains(resourceType)) {
            throw new IllegalArgumentException("Unsupported resource type");
        }
        long offset = (long) page * size;
        var result = events.find(new SecurityAuditEventQueryPort.Filter(type, actorUserId, subjectUserId,
                resourceType, resourceId), size, offset);
        return new Page(result.events(), page, size, result.totalElements(),
                (int) ((result.totalElements() + size - 1) / size));
    }

    public record Page(List<SecurityAuditEventQueryPort.Event> items, int page, int size,
            long totalElements, int totalPages) { }
}
