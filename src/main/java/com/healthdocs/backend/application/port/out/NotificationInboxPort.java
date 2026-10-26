package com.healthdocs.backend.application.port.out;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface NotificationInboxPort {
    Page findByRecipient(UUID recipientUserId, int limit, long offset);
    Optional<Event> markRead(UUID notificationId, UUID recipientUserId, Instant readAt);
    boolean emailEnabled(UUID userId, String notificationType);
    void setEmailEnabled(UUID userId, String notificationType, boolean enabled, Instant updatedAt);

    record Page(List<Event> events, long totalElements) { }
    record Event(UUID id, String type, String caseReferenceCode, Instant createdAt, Instant readAt) { }
}
