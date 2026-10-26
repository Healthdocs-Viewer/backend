package com.healthdocs.backend.adapter.in.web;

import com.healthdocs.backend.application.port.out.AuthProvider.AuthenticatedUser;
import com.healthdocs.backend.application.service.SecurityAuditQueryService;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/security-audit")
public class AdminSecurityAuditController {
    private final SecurityAuditQueryService audit;

    public AdminSecurityAuditController(SecurityAuditQueryService audit) {
        this.audit = audit;
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN') and hasAuthority('ACCOUNT_ENABLED')")
    public SecurityAuditQueryService.Page list(@AuthenticationPrincipal AuthenticatedUser user,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String type, @RequestParam(required = false) UUID actorUserId,
            @RequestParam(required = false) UUID subjectUserId, @RequestParam(required = false) String resourceType,
            @RequestParam(required = false) UUID resourceId) {
        return audit.execute(user, page, size, type, actorUserId, subjectUserId, resourceType, resourceId);
    }
}
