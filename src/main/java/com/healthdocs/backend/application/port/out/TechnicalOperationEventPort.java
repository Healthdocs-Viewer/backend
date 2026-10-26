package com.healthdocs.backend.application.port.out;

import java.time.Instant;
import java.util.UUID;

/** Append-only technical operation telemetry. It is separate from business activity and inbox state. */
public interface TechnicalOperationEventPort {
    void record(Event event);

    record Event(String eventType, String operationType, UUID operationId, UUID correlationId, UUID caseId,
            UUID resourceId, int attemptNumber, String errorCode, Instant occurredAt, String deduplicationKey) { }
}
