package com.healthdocs.backend.adapter.in.web;

import com.healthdocs.backend.application.port.out.AuthProvider.AuthenticatedUser;
import com.healthdocs.backend.application.service.NotificationInboxService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/notifications")
@PreAuthorize("hasRole('PATIENT') and hasAuthority('ACCOUNT_ENABLED')")
public class NotificationInboxController {
    private final NotificationInboxService notifications;

    public NotificationInboxController(NotificationInboxService notifications) {
        this.notifications = notifications;
    }

    @GetMapping
    public NotificationInboxService.Page list(@AuthenticationPrincipal AuthenticatedUser user,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return notifications.list(user, page, size);
    }

    @PostMapping("/{notificationId}/read")
    public com.healthdocs.backend.application.port.out.NotificationInboxPort.Event markRead(
            @AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID notificationId) {
        return notifications.markRead(user, notificationId);
    }

    @GetMapping("/preferences")
    public NotificationInboxService.Preferences preferences(@AuthenticationPrincipal AuthenticatedUser user) {
        return notifications.preferences(user);
    }

    @PutMapping("/preferences/email")
    public NotificationInboxService.Preferences setEmailPreference(
            @AuthenticationPrincipal AuthenticatedUser user, @Valid @RequestBody EmailPreferenceRequest request) {
        return notifications.setEmailEnabled(user, request.enabled());
    }

    public record EmailPreferenceRequest(@NotNull Boolean enabled) { }
}
