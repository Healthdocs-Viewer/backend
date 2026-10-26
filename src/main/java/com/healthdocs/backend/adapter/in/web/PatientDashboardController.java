package com.healthdocs.backend.adapter.in.web;

import com.healthdocs.backend.application.port.out.AuthProvider.AuthenticatedUser;
import com.healthdocs.backend.application.service.PatientDashboardUseCase;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/patient")
public class PatientDashboardController {
    private final PatientDashboardUseCase dashboard;

    public PatientDashboardController(PatientDashboardUseCase dashboard) {
        this.dashboard = dashboard;
    }

    @GetMapping("/dashboard")
    @PreAuthorize("hasRole('PATIENT') and hasAuthority('ACCOUNT_ENABLED')")
    public PatientDashboardUseCase.Response getDashboard(@AuthenticationPrincipal AuthenticatedUser user) {
        return dashboard.execute(user);
    }
}
