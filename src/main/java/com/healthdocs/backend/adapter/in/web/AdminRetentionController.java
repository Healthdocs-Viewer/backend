package com.healthdocs.backend.adapter.in.web;

import com.healthdocs.backend.application.port.out.AuthProvider.AuthenticatedUser;
import com.healthdocs.backend.application.service.RetentionLifecycleService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/retention")
@PreAuthorize("hasRole('ADMIN') and hasAuthority('ACCOUNT_ENABLED')")
public class AdminRetentionController {
    private final RetentionLifecycleService retention;

    public AdminRetentionController(RetentionLifecycleService retention) {
        this.retention = retention;
    }

    @GetMapping("/deletion-requests")
    public RetentionLifecycleService.DeletionRequestPage listDeletionRequests(
            @AuthenticationPrincipal AuthenticatedUser actor,
            @RequestParam String targetType,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return retention.listDeletionRequests(actor, targetType, status, page, size);
    }

    @PostMapping("/cases/{caseId}/archive")
    public RetentionLifecycleService.CaseArchive archiveCase(@AuthenticationPrincipal AuthenticatedUser actor,
            @PathVariable UUID caseId) {
        return retention.archiveCase(actor, caseId);
    }

    @PostMapping("/case-deletion-requests/{requestId}/decision")
    public RetentionLifecycleService.DeletionRequest decideCaseDeletion(
            @AuthenticationPrincipal AuthenticatedUser actor, @PathVariable UUID requestId,
            @Valid @RequestBody DecisionRequest request) {
        return retention.decideCaseDeletion(actor, requestId, request.decision());
    }

    @PostMapping("/file-deletion-requests/{requestId}/decision")
    public RetentionLifecycleService.DeletionRequest decideFileDeletion(
            @AuthenticationPrincipal AuthenticatedUser actor, @PathVariable UUID requestId,
            @Valid @RequestBody DecisionRequest request) {
        return retention.decideFileDeletion(actor, requestId, request.decision());
    }

    public record DecisionRequest(@NotNull RetentionLifecycleService.Decision decision) { }
}
