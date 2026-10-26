package com.healthdocs.backend.adapter.in.web;

import com.healthdocs.backend.application.port.out.AuthProvider.AuthenticatedUser;
import com.healthdocs.backend.application.service.CaseActivityQueryService;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/cases/{caseId}/activity")
public class CaseActivityController {
    private final CaseActivityQueryService activity;

    public CaseActivityController(CaseActivityQueryService activity) {
        this.activity = activity;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('PATIENT','RESEARCHER','COORDINATOR') and hasAuthority('ACCOUNT_ENABLED')")
    public CaseActivityQueryService.Page list(@AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable UUID caseId, @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return activity.execute(user, caseId, page, size);
    }
}
