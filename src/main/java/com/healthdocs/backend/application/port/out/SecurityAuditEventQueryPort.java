package com.healthdocs.backend.application.port.out;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface SecurityAuditEventQueryPort {
    Page find(Filter filter, int limit, long offset);

    record Filter(String type, UUID actorUserId, UUID subjectUserId, String resourceType, UUID resourceId) { }

    record Page(List<Event> events, long totalElements) { }

    record Event(UUID id, String type, Instant occurredAt, UUID actorUserId, UUID subjectUserId,
            UUID caseId, String resourceType, UUID resourceId, String action, String changedField,
            String previousValue, String newValue) { }
}
