package com.healthdocs.backend.adapter.in.web;

import com.healthdocs.backend.application.port.out.AuthProvider.AuthenticatedUser;
import com.healthdocs.backend.application.service.InputChangeDraftService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/cases/{caseId}/input-change-requests")
@PreAuthorize("hasAnyRole('PATIENT', 'RESEARCHER', 'COORDINATOR', 'ADMIN') and hasAuthority('ACCOUNT_ENABLED')")
public class InputChangeDraftController {
    private final InputChangeDraftService drafts;

    public InputChangeDraftController(InputChangeDraftService drafts) {
        this.drafts = drafts;
    }

    @GetMapping
    public List<InputChangeDraftService.RequestView> list(@AuthenticationPrincipal AuthenticatedUser actor,
            @PathVariable UUID caseId) {
        return drafts.list(actor, caseId);
    }

    @PostMapping
    public ResponseEntity<InputChangeDraftService.RequestView> open(@AuthenticationPrincipal AuthenticatedUser actor,
            @PathVariable UUID caseId) {
        return ResponseEntity.status(HttpStatus.CREATED).body(drafts.open(actor, caseId));
    }

    @GetMapping("/{requestId}")
    public InputChangeDraftService.RequestView get(@AuthenticationPrincipal AuthenticatedUser actor,
            @PathVariable UUID caseId, @PathVariable UUID requestId) {
        return drafts.get(actor, caseId, requestId);
    }

    @PatchMapping("/{requestId}")
    public InputChangeDraftService.RequestView updateReason(@AuthenticationPrincipal AuthenticatedUser actor,
            @PathVariable UUID caseId, @PathVariable UUID requestId,
            @Valid @RequestBody ReasonRequest body) {
        return drafts.updateReason(actor, caseId, requestId, body.reason());
    }

    @PostMapping("/{requestId}/uploads")
    public ResponseEntity<InputChangeDraftService.UploadIntent> createUpload(
            @AuthenticationPrincipal AuthenticatedUser actor,
            @PathVariable UUID caseId, @PathVariable UUID requestId,
            @Valid @RequestBody UploadRequest body) {
        return ResponseEntity.status(HttpStatus.CREATED).body(drafts.createUpload(actor, caseId, requestId,
                body.originalFilename(), body.contentType(), body.sizeBytes()));
    }

    @DeleteMapping("/{requestId}/files/{fileId}")
    public ResponseEntity<Void> remove(@AuthenticationPrincipal AuthenticatedUser actor,
            @PathVariable UUID caseId, @PathVariable UUID requestId, @PathVariable UUID fileId) {
        drafts.remove(actor, caseId, requestId, fileId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{requestId}/cancellation")
    public ResponseEntity<Void> cancel(@AuthenticationPrincipal AuthenticatedUser actor,
            @PathVariable UUID caseId, @PathVariable UUID requestId) {
        drafts.cancel(actor, caseId, requestId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{requestId}/submission")
    public ResponseEntity<Void> submit(@AuthenticationPrincipal AuthenticatedUser actor,
            @PathVariable UUID caseId, @PathVariable UUID requestId) {
        drafts.submit(actor, caseId, requestId);
        return ResponseEntity.noContent().build();
    }

    public record ReasonRequest(@NotBlank @Size(max = 250) String reason) { }
    public record UploadRequest(@NotBlank String originalFilename, @NotBlank String contentType,
            @NotNull @Positive Long sizeBytes) { }
}
