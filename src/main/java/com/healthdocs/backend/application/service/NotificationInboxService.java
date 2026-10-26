package com.healthdocs.backend.application.service;

import com.healthdocs.backend.application.port.out.AuthProvider.AuthenticatedUser;
import com.healthdocs.backend.application.port.out.NotificationInboxPort;
import com.healthdocs.backend.domain.model.Role;
import java.time.Clock;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class NotificationInboxService {
    private static final int MAX_SIZE = 100;
    private static final String RESULT_AVAILABLE = "RESULT_AVAILABLE";
    private final NotificationInboxPort inbox;
    private final Clock clock;

    public NotificationInboxService(NotificationInboxPort inbox, Clock clock) {
        this.inbox = inbox;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public Page list(AuthenticatedUser user, int page, int size) {
        requirePatient(user);
        if (page < 0) throw new IllegalArgumentException("page must be zero or greater");
        if (size < 1 || size > MAX_SIZE) throw new IllegalArgumentException("size must be between 1 and 100");
        long offset = (long) page * size;
        var result = inbox.findByRecipient(user.id(), size, offset);
        int totalPages = result.totalElements() == 0 ? 0
                : (int) Math.ceil((double) result.totalElements() / size);
        return new Page(result.events(), page, size, result.totalElements(), totalPages);
    }

    @Transactional
    public NotificationInboxPort.Event markRead(AuthenticatedUser user, UUID notificationId) {
        requirePatient(user);
        if (notificationId == null) throw new IllegalArgumentException("Notification ID is required");
        return inbox.markRead(notificationId, user.id(), clock.instant())
                .orElseThrow(() -> new ResourceNotFoundException("Notification not found"));
    }

    @Transactional(readOnly = true)
    public Preferences preferences(AuthenticatedUser user) {
        requirePatient(user);
        return new Preferences(inbox.emailEnabled(user.id(), RESULT_AVAILABLE));
    }

    @Transactional
    public Preferences setEmailEnabled(AuthenticatedUser user, boolean enabled) {
        requirePatient(user);
        inbox.setEmailEnabled(user.id(), RESULT_AVAILABLE, enabled, clock.instant());
        return new Preferences(enabled);
    }

    private static void requirePatient(AuthenticatedUser user) {
        if (user == null || !user.enabled() || user.role() != Role.PATIENT) {
            throw new ForbiddenOperationException("Patient access required");
        }
    }

    public record Page(List<NotificationInboxPort.Event> items, int page, int size,
            long totalElements, int totalPages) { }
    public record Preferences(boolean emailForResultsEnabled) { }
}
