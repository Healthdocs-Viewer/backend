package com.healthdocs.backend.application.port.out;

import java.time.Instant;
import java.util.UUID;

/** Writes a notification trigger to the caller's business transaction. */
public interface NotificationOutboxPort {
    void enqueue(String eventType, String sourceEventKey, UUID caseId, UUID recipientUserId,
            UUID publishedByUserId, String relatedEntityType, UUID relatedEntityId, Instant occurredAt);
}
