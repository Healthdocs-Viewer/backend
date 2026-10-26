package com.healthdocs.backend.adapter.in.web;

import com.healthdocs.backend.application.port.out.AuthProvider.AuthenticatedUser;
import com.healthdocs.backend.application.service.AssignCaseUseCase;
import com.healthdocs.backend.application.service.ListInternalUsersUseCase;
import com.healthdocs.backend.application.service.ListPendingCasesUseCase;
import com.healthdocs.backend.application.service.ResearcherDirectoryUseCase;
import com.healthdocs.backend.application.service.ChangeResearcherAuthorizationUseCase;
import com.healthdocs.backend.application.service.UpdateResearcherProfileUseCase;
import com.healthdocs.backend.application.service.AcademicAssignmentsUseCase;
import com.healthdocs.backend.application.service.CoordinatorAcademicCaseDetailUseCase;
import com.healthdocs.backend.domain.model.ResearcherAuthorizationStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
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
    private final ResearcherDirectoryUseCase researcherDirectory;
    private final ChangeResearcherAuthorizationUseCase changeResearcherAuthorization;
    private final UpdateResearcherProfileUseCase updateResearcherProfile;
    private final AcademicAssignmentsUseCase academicAssignments;
    private final CoordinatorAcademicCaseDetailUseCase academicCaseDetail;
    private final com.healthdocs.backend.application.service.InternalResultsQueryService resultQueries;

    public OperationsController(
            AssignCaseUseCase assignCase,
            ListInternalUsersUseCase listInternalUsers,
            ListPendingCasesUseCase listPendingCases,
            com.healthdocs.backend.application.service.InternalFlowService internalFlow,
            ResearcherDirectoryUseCase researcherDirectory,
            ChangeResearcherAuthorizationUseCase changeResearcherAuthorization,
            UpdateResearcherProfileUseCase updateResearcherProfile,
            AcademicAssignmentsUseCase academicAssignments,
            CoordinatorAcademicCaseDetailUseCase academicCaseDetail,
            com.healthdocs.backend.application.service.InternalResultsQueryService resultQueries) {
        this.assignCase = assignCase;
        this.listInternalUsers = listInternalUsers;
        this.listPendingCases = listPendingCases;
        this.internalFlow = internalFlow;
        this.researcherDirectory = researcherDirectory;
        this.changeResearcherAuthorization = changeResearcherAuthorization;
        this.updateResearcherProfile = updateResearcherProfile;
        this.academicAssignments = academicAssignments;
        this.academicCaseDetail = academicCaseDetail;
        this.resultQueries = resultQueries;
    }

    @GetMapping("/results")
    @PreAuthorize("hasRole('COORDINATOR') and hasAuthority('ACCOUNT_ENABLED')")
    public com.healthdocs.backend.application.service.InternalResultsQueryService.CoordinatorResults listResults(
            @AuthenticationPrincipal AuthenticatedUser coordinator,
            @org.springframework.web.bind.annotation.RequestParam(required = false) String q,
            @org.springframework.web.bind.annotation.RequestParam(required = false) com.healthdocs.backend.domain.model.ResultStatus status,
            @org.springframework.web.bind.annotation.RequestParam(defaultValue = "uploadedAt,desc") String sort,
            @org.springframework.web.bind.annotation.RequestParam(defaultValue = "0") int page,
            @org.springframework.web.bind.annotation.RequestParam(defaultValue = "100") int size) {
        return resultQueries.coordinator(coordinator, q, status, sort, page, size);
    }

    @GetMapping("/cases/pending")
    @PreAuthorize("hasRole('COORDINATOR') and hasAuthority('ACCOUNT_ENABLED')")
    public List<ListPendingCasesUseCase.PendingCaseView> listPendingCases(
            @AuthenticationPrincipal AuthenticatedUser coordinator) {
        return listPendingCases.execute(coordinator);
    }

    @GetMapping("/users/internal")
    @PreAuthorize("hasRole('COORDINATOR') and hasAuthority('ACCOUNT_ENABLED')")
    public List<ListInternalUsersUseCase.InternalUser> listInternalUsers(
            @AuthenticationPrincipal AuthenticatedUser coordinator) {
        return listInternalUsers.execute(coordinator);
    }

    @GetMapping("/researchers")
    @PreAuthorize("hasAnyRole('COORDINATOR','ADMIN') and hasAuthority('ACCOUNT_ENABLED')")
    public ResearcherDirectoryUseCase.DirectoryResponse listResearchers(
            @AuthenticationPrincipal AuthenticatedUser requester,
            @org.springframework.web.bind.annotation.RequestParam(required = false) String q,
            @org.springframework.web.bind.annotation.RequestParam(required = false) ResearcherAuthorizationStatus status,
            @org.springframework.web.bind.annotation.RequestParam(defaultValue = "name,asc") String sort,
            @org.springframework.web.bind.annotation.RequestParam(defaultValue = "0") int page,
            @org.springframework.web.bind.annotation.RequestParam(defaultValue = "100") int size) {
        return researcherDirectory.execute(requester, q, status, sort, page, size);
    }

    @PutMapping("/researchers/{userId}/authorization")
    @PreAuthorize("hasAnyRole('COORDINATOR','ADMIN') and hasAuthority('ACCOUNT_ENABLED')")
    public ResearcherDirectoryUseCase.ResearcherView changeResearcherAuthorization(
            @AuthenticationPrincipal AuthenticatedUser actor, @PathVariable UUID userId,
            @Valid @RequestBody AuthorizationRequest request) {
        ResearcherAuthorizationStatus status;
        try { status = ResearcherAuthorizationStatus.valueOf(request.authorizationStatus().trim().toUpperCase(java.util.Locale.ROOT)); }
        catch (IllegalArgumentException ex) { throw new IllegalArgumentException("Authorization status must be AUTHORIZED or RESTRICTED", ex); }
        return changeResearcherAuthorization.execute(actor, userId, status);
    }

    @PutMapping("/researchers/{userId}/profile")
    @PreAuthorize("hasAnyRole('COORDINATOR','ADMIN') and hasAuthority('ACCOUNT_ENABLED')")
    public ResearcherDirectoryUseCase.ResearcherView updateResearcherProfile(
            @AuthenticationPrincipal AuthenticatedUser actor, @PathVariable UUID userId,
            @Valid @RequestBody ResearcherProfileRequest request) {
        return updateResearcherProfile.execute(actor, userId, request.fullName(), request.researcherCode());
    }

    @PostMapping("/cases/{caseId}/assignments")
    @PreAuthorize("hasAnyRole('COORDINATOR','RESEARCHER') and hasAuthority('ACCOUNT_ENABLED')")
    public ResponseEntity<Void> assignCase(
            @AuthenticationPrincipal AuthenticatedUser coordinator,
            @PathVariable UUID caseId,
            @Valid @RequestBody AssignRequest request) {
        assignCase.execute(coordinator, caseId, request.userId());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/cases/{caseId}/assignments")
    @PreAuthorize("hasAnyRole('COORDINATOR','ADMIN') and hasAuthority('ACCOUNT_ENABLED')")
    public AcademicAssignmentsUseCase.CaseAssignmentsView getCaseAssignments(
            @AuthenticationPrincipal AuthenticatedUser actor, @PathVariable UUID caseId) {
        return academicAssignments.forCase(actor, caseId);
    }

    @PutMapping("/cases/{caseId}/assignments")
    @PreAuthorize("hasAnyRole('COORDINATOR','ADMIN') and hasAuthority('ACCOUNT_ENABLED')")
    public AcademicAssignmentsUseCase.CaseAssignmentsView replaceCaseAssignments(
            @AuthenticationPrincipal AuthenticatedUser actor, @PathVariable UUID caseId,
            @Valid @RequestBody ReplaceCaseAssignmentsRequest request) {
        return academicAssignments.replaceCaseResearchers(actor, caseId, request.researcherIds());
    }

    @GetMapping("/cases/academic")
    @PreAuthorize("hasRole('COORDINATOR') and hasAuthority('ACCOUNT_ENABLED')")
    public AcademicAssignmentsUseCase.CoordinatorAcademicCasesResponse listAcademicCases(
            @AuthenticationPrincipal AuthenticatedUser actor,
            @org.springframework.web.bind.annotation.RequestParam(required = false) String q,
            @org.springframework.web.bind.annotation.RequestParam(required = false) com.healthdocs.backend.domain.model.CaseStatus status,
            @org.springframework.web.bind.annotation.RequestParam(defaultValue = "receivedAt,desc") String sort,
            @org.springframework.web.bind.annotation.RequestParam(defaultValue = "0") int page,
            @org.springframework.web.bind.annotation.RequestParam(defaultValue = "100") int size) {
        return academicAssignments.coordinatorCases(actor, q, status, sort, page, size);
    }

    @GetMapping("/cases/academic/{caseId}")
    @PreAuthorize("hasRole('COORDINATOR') and hasAuthority('ACCOUNT_ENABLED')")
    public CoordinatorAcademicCaseDetailUseCase.CoordinatorAcademicCaseDetail getAcademicCaseDetail(
            @AuthenticationPrincipal AuthenticatedUser actor, @PathVariable UUID caseId) {
        return academicCaseDetail.execute(actor, caseId);
    }

    @GetMapping("/researchers/{researcherId}/cases")
    @PreAuthorize("hasAnyRole('COORDINATOR','ADMIN') and hasAuthority('ACCOUNT_ENABLED')")
    public AcademicAssignmentsUseCase.AcademicCasesResponse getResearcherCases(
            @AuthenticationPrincipal AuthenticatedUser actor, @PathVariable UUID researcherId,
            @org.springframework.web.bind.annotation.RequestParam(required = false) String q,
            @org.springframework.web.bind.annotation.RequestParam(defaultValue = "receivedAt,desc") String sort,
            @org.springframework.web.bind.annotation.RequestParam(defaultValue = "0") int page,
            @org.springframework.web.bind.annotation.RequestParam(defaultValue = "100") int size) {
        return academicAssignments.casesForResearcher(actor, researcherId, q, sort, page, size);
    }

    @PutMapping("/researchers/{researcherId}/case-assignments")
    @PreAuthorize("hasAnyRole('COORDINATOR','ADMIN') and hasAuthority('ACCOUNT_ENABLED')")
    public ResponseEntity<Void> replaceResearcherCases(@AuthenticationPrincipal AuthenticatedUser actor,
            @PathVariable UUID researcherId, @Valid @RequestBody ReplaceResearcherCasesRequest request) {
        academicAssignments.replaceResearcherCases(actor, researcherId, request.caseIds());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/results/{resultId}/availability")
    @PreAuthorize("hasRole('COORDINATOR') and hasAuthority('ACCOUNT_ENABLED')")
    public ResponseEntity<Void> makeResultAvailable(
            @AuthenticationPrincipal AuthenticatedUser coordinator,
            @PathVariable UUID resultId) {
        internalFlow.makeResultAvailable(coordinator, resultId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/results/{resultId}/correction-requests")
    @PreAuthorize("hasRole('COORDINATOR') and hasAuthority('ACCOUNT_ENABLED')")
    public ResponseEntity<Void> requestResultCorrection(
            @AuthenticationPrincipal AuthenticatedUser coordinator,
            @PathVariable UUID resultId,
            @Valid @RequestBody CorrectionRequest request) {
        internalFlow.requestCorrection(coordinator, resultId, request.reason());
        return ResponseEntity.noContent().build();
    }

    public record AssignRequest(@NotNull UUID userId) {}
    public record ReplaceCaseAssignmentsRequest(@NotNull List<@NotNull UUID> researcherIds) {}
    public record ReplaceResearcherCasesRequest(@NotNull List<@NotNull UUID> caseIds) {}
    public record AuthorizationRequest(@jakarta.validation.constraints.NotBlank String authorizationStatus) {}
    public record ResearcherProfileRequest(@jakarta.validation.constraints.NotBlank String fullName,
            @jakarta.validation.constraints.NotBlank String researcherCode) {}
    public record CorrectionRequest(@jakarta.validation.constraints.NotBlank String reason) {}
}
