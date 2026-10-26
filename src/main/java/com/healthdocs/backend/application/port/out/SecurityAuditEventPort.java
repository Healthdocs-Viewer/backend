package com.healthdocs.backend.application.port.out;

import java.time.Instant;
import java.util.UUID;

/** Appends restricted account and sensitive-resource audit events in the caller's transaction. */
public interface SecurityAuditEventPort {
    void record(String type, Instant occurredAt, UUID actorUserId, UUID subjectUserId, UUID caseId,
            String resourceType, UUID resourceId, String action, String changedField,
            String previousValue, String newValue, String deduplicationKey);
}
