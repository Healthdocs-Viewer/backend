package com.healthdocs.backend.adapter.in.web;

import com.healthdocs.backend.application.port.out.AuthProvider.AuthenticatedUser;
import com.healthdocs.backend.application.service.InputChangeDraftService;
import com.healthdocs.backend.application.service.InputChangeReviewService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/operations/input-change-requests")
@PreAuthorize("hasRole('COORDINATOR') and hasAuthority('ACCOUNT_ENABLED')")
public class InputChangeReviewController {
    private final InputChangeReviewService reviews;

    public InputChangeReviewController(InputChangeReviewService reviews) {
        this.reviews = reviews;
    }

    @GetMapping
    public List<InputChangeReviewService.RequestSummary> listPending(
            @AuthenticationPrincipal AuthenticatedUser coordinator) {
        return reviews.listPending(coordinator);
    }

    @GetMapping("/{requestId}")
    public InputChangeDraftService.RequestView getForReview(
            @AuthenticationPrincipal AuthenticatedUser coordinator, @PathVariable UUID requestId) {
        return reviews.getForReview(coordinator, requestId);
    }

    @PostMapping("/{requestId}/files/{fileId}/download")
    public InputChangeReviewService.DownloadView createDownload(
            @AuthenticationPrincipal AuthenticatedUser coordinator, @PathVariable UUID requestId,
            @PathVariable UUID fileId) {
        return reviews.createDownload(coordinator, requestId, fileId);
    }

    @PostMapping("/{requestId}/approval")
    public ResponseEntity<Void> approve(
            @AuthenticationPrincipal AuthenticatedUser coordinator, @PathVariable UUID requestId) {
        reviews.approve(coordinator, requestId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{requestId}/rejection")
    public ResponseEntity<Void> reject(
            @AuthenticationPrincipal AuthenticatedUser coordinator, @PathVariable UUID requestId,
            @Valid @RequestBody RejectionRequest body) {
        reviews.reject(coordinator, requestId, body.reason());
        return ResponseEntity.noContent().build();
    }

    public record RejectionRequest(@NotBlank @Size(max = 1000) String reason) { }
}
