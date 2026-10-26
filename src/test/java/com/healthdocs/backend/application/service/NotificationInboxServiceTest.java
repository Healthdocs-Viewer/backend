package com.healthdocs.backend.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.healthdocs.backend.application.port.out.AuthProvider.AuthenticatedUser;
import com.healthdocs.backend.application.port.out.NotificationInboxPort;
import com.healthdocs.backend.domain.model.Role;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class NotificationInboxServiceTest {
    @Test
    void queryIsScopedToCurrentPatientAndUsesPaging() {
        UUID patientId = UUID.randomUUID();
        RecordingInbox inbox = new RecordingInbox();
        var service = new NotificationInboxService(inbox,
                Clock.fixed(Instant.parse("2026-10-24T12:00:00Z"), ZoneOffset.UTC));
        var user = new AuthenticatedUser(patientId, "ignored", Role.PATIENT, true);

        var page = service.list(user, 1, 5);

        assertEquals(patientId, inbox.recipient);
        assertEquals(5, inbox.limit);
        assertEquals(5L, inbox.offset);
        assertEquals(2, page.totalPages());
    }

    @Test
    void rejectsNonPatientDisabledUserAndInvalidPageBeforeQuery() {
        RecordingInbox inbox = new RecordingInbox();
        var service = new NotificationInboxService(inbox, Clock.systemUTC());
        assertThrows(ForbiddenOperationException.class, () -> service.list(
                new AuthenticatedUser(UUID.randomUUID(), "", Role.RESEARCHER, true), 0, 20));
        assertThrows(ForbiddenOperationException.class, () -> service.list(
                new AuthenticatedUser(UUID.randomUUID(), "", Role.PATIENT, false), 0, 20));
        assertThrows(ForbiddenOperationException.class, () -> service.preferences(
                new AuthenticatedUser(UUID.randomUUID(), "", Role.RESEARCHER, true)));
        assertThrows(ForbiddenOperationException.class, () -> service.setEmailEnabled(
                new AuthenticatedUser(UUID.randomUUID(), "", Role.COORDINATOR, true), true));
        assertThrows(ForbiddenOperationException.class, () -> service.markRead(
                new AuthenticatedUser(UUID.randomUUID(), "", Role.RESEARCHER, true), UUID.randomUUID()));
        assertThrows(ForbiddenOperationException.class, () -> service.markRead(
                new AuthenticatedUser(UUID.randomUUID(), "", Role.PATIENT, false), UUID.randomUUID()));
        assertThrows(IllegalArgumentException.class, () -> service.list(
                new AuthenticatedUser(UUID.randomUUID(), "", Role.PATIENT, true), 0, 101));
        assertEquals(0, inbox.findCalls);
    }

    @Test
    void marksOnlyOwnedNotificationAndAllowsEmailOptIn() {
        UUID patientId = UUID.randomUUID();
        UUID notificationId = UUID.randomUUID();
        RecordingInbox inbox = new RecordingInbox();
        var service = new NotificationInboxService(inbox,
                Clock.fixed(Instant.parse("2026-10-24T12:00:00Z"), ZoneOffset.UTC));
        var user = new AuthenticatedUser(patientId, "", Role.PATIENT, true);

        service.markRead(user, notificationId);
        assertEquals(notificationId, inbox.markedId);
        assertEquals(patientId, inbox.markedRecipient);
        assertEquals(Instant.parse("2026-10-24T12:00:00Z"), inbox.markedAt);
        assertEquals(false, service.preferences(user).emailForResultsEnabled());
        assertEquals(true, service.setEmailEnabled(user, true).emailForResultsEnabled());
        assertEquals(true, inbox.emailEnabled);
        assertThrows(ResourceNotFoundException.class, () -> {
            inbox.hideNotification = true;
            service.markRead(user, notificationId);
        });
    }

    private static final class RecordingInbox implements NotificationInboxPort {
        private UUID recipient;
        private UUID markedId;
        private UUID markedRecipient;
        private Instant markedAt;
        private int limit;
        private long offset;
        private int findCalls;
        private boolean emailEnabled;
        private boolean hideNotification;

        @Override
        public Page findByRecipient(UUID recipientUserId, int limit, long offset) {
            findCalls++;
            this.recipient = recipientUserId;
            this.limit = limit;
            this.offset = offset;
            return new Page(List.of(), 6);
        }

        @Override
        public Optional<Event> markRead(UUID notificationId, UUID recipientUserId, Instant readAt) {
            markedId = notificationId;
            markedRecipient = recipientUserId;
            markedAt = readAt;
            if (hideNotification) return Optional.empty();
            return Optional.of(new Event(notificationId, "RESULT_AVAILABLE", "CAS-0001", readAt, readAt));
        }

        @Override
        public boolean emailEnabled(UUID userId, String notificationType) { return emailEnabled; }

        @Override
        public void setEmailEnabled(UUID userId, String notificationType, boolean enabled, Instant updatedAt) {
            emailEnabled = enabled;
        }
    }
}
