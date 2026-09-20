package com.healthdocs.backend.domain.model;

import java.time.Instant;
import java.util.UUID;

public record ResultAvailableEvent(int eventVersion, String eventType, UUID eventId, UUID resultId,
        UUID caseId, UUID patientId, String patientEmail, Instant availableAt,
        Instant eventTimestamp, UUID publishedBy) {
    public static final int CURRENT_VERSION = 1;
    public static final String EVENT_TYPE = "RESULT_AVAILABLE";
}
