package com.healthdocs.backend.application.port.out;

import java.time.Instant;
import java.util.UUID;

/** Output port for appending a business activity event to the current transaction. */
public interface ActivityEventPort {
    void record(String type, Instant occurredAt, UUID caseId, UUID actorUserId,
            UUID subjectUserId, String relatedEntityType, UUID relatedEntityId, String deduplicationKey);
}
