package com.healthdocs.backend.adapter.in.web;

import com.healthdocs.backend.application.port.out.AuthProvider.AuthenticatedUser;
import com.healthdocs.backend.application.service.RetentionLifecycleService;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/cases")
public class RetentionRequestController {
    private final RetentionLifecycleService retention;

    public RetentionRequestController(RetentionLifecycleService retention) {
        this.retention = retention;
    }

    @PostMapping("/{caseId}/deletion-requests")
    @PreAuthorize("hasRole('PATIENT') and hasAuthority('ACCOUNT_ENABLED')")
    public ResponseEntity<RetentionLifecycleService.DeletionRequest> requestCaseDeletion(
            @AuthenticationPrincipal AuthenticatedUser actor, @PathVariable UUID caseId,
            @RequestHeader("Idempotency-Key") UUID idempotencyKey) {
        var result = retention.requestCaseDeletion(actor, caseId, idempotencyKey);
        return ResponseEntity.status(result.replayed() ? HttpStatus.OK : HttpStatus.ACCEPTED).body(result);
    }

    @PostMapping("/{caseId}/files/{fileId}/deletion-requests")
    @PreAuthorize("hasRole('PATIENT') and hasAuthority('ACCOUNT_ENABLED')")
    public ResponseEntity<RetentionLifecycleService.DeletionRequest> requestFileDeletion(
            @AuthenticationPrincipal AuthenticatedUser actor, @PathVariable UUID caseId, @PathVariable UUID fileId,
            @RequestHeader("Idempotency-Key") UUID idempotencyKey) {
        var result = retention.requestFileDeletion(actor, caseId, fileId, idempotencyKey);
        return ResponseEntity.status(result.replayed() ? HttpStatus.OK : HttpStatus.ACCEPTED).body(result);
    }
}
