package com.healthdocs.backend.adapter.in.web;

import com.healthdocs.backend.application.port.out.AuthProvider.AuthenticatedUser;
import com.healthdocs.backend.application.service.AssignCaseUseCase;
import com.healthdocs.backend.application.service.ListInternalUsersUseCase;
import com.healthdocs.backend.application.service.ListPendingCasesUseCase;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.List;

@RestController
@RequestMapping("/api/v1/operations")
public class OperationsController {
    private final AssignCaseUseCase assignCase;
    private final ListInternalUsersUseCase listInternalUsers;
    private final ListPendingCasesUseCase listPendingCases;
    private final com.healthdocs.backend.application.service.InternalFlowService internalFlow;

    public OperationsController(
            AssignCaseUseCase assignCase,
            ListInternalUsersUseCase listInternalUsers,
            ListPendingCasesUseCase listPendingCases,
            com.healthdocs.backend.application.service.InternalFlowService internalFlow) {
        this.assignCase = assignCase;
        this.listInternalUsers = listInternalUsers;
        this.listPendingCases = listPendingCases;
        this.internalFlow = internalFlow;
    }

    @GetMapping("/cases/pending")
    @PreAuthorize("hasRole('PROFESOR') and hasAuthority('ACCOUNT_ENABLED')")
    public List<ListPendingCasesUseCase.PendingCaseView> listPendingCases(
            @AuthenticationPrincipal AuthenticatedUser professor) {
        return listPendingCases.execute(professor);
    }

    @GetMapping("/users/internal")
    @PreAuthorize("hasRole('PROFESOR') and hasAuthority('ACCOUNT_ENABLED')")
    public List<ListInternalUsersUseCase.InternalUser> listInternalUsers(
            @AuthenticationPrincipal AuthenticatedUser professor) {
        return listInternalUsers.execute(professor);
    }

    @PostMapping("/cases/{caseId}/assignments")
    @PreAuthorize("hasRole('PROFESOR')")
    public ResponseEntity<Void> assignCase(
            @AuthenticationPrincipal AuthenticatedUser professor,
            @PathVariable UUID caseId,
            @Valid @RequestBody AssignRequest request) {
        assignCase.execute(professor, caseId, request.userId());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/results/{resultId}/availability")
    @PreAuthorize("hasRole('PROFESOR') and hasAuthority('ACCOUNT_ENABLED')")
    public ResponseEntity<Void> makeResultAvailable(
            @AuthenticationPrincipal AuthenticatedUser professor,
            @PathVariable UUID resultId) {
        internalFlow.makeResultAvailable(professor, resultId);
        return ResponseEntity.noContent().build();
    }

    public record AssignRequest(@NotNull UUID userId) {}
}
