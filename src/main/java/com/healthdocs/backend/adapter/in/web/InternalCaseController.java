package com.healthdocs.backend.adapter.in.web;

import com.healthdocs.backend.application.port.out.AuthProvider.AuthenticatedUser;
import com.healthdocs.backend.application.service.InternalFlowService;
import com.healthdocs.backend.application.service.InternalResultsQueryService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
@PreAuthorize("hasAnyRole('RESEARCHER','COORDINATOR')")
public class InternalCaseController {
    private final InternalFlowService flow;
    private final InternalResultsQueryService resultQueries;
    public InternalCaseController(InternalFlowService flow, InternalResultsQueryService resultQueries){this.flow=flow;this.resultQueries=resultQueries;}
    @GetMapping("/internal/results") public InternalResultsQueryService.ResearcherResults listResults(@AuthenticationPrincipal AuthenticatedUser user,
            @RequestParam(required=false) String q, @RequestParam(required=false) com.healthdocs.backend.domain.model.ResultStatus status,
            @RequestParam(defaultValue="uploadedAt,desc") String sort, @RequestParam(defaultValue="0") int page,
            @RequestParam(defaultValue="100") int size){return resultQueries.researcher(user,q,status,sort,page,size);}
    @GetMapping("/internal/cases/{caseId}/results/history") public InternalResultsQueryService.History resultHistory(@AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable UUID caseId, @RequestParam(defaultValue="0") int page, @RequestParam(defaultValue="100") int size){return resultQueries.history(user,caseId,page,size);}
    @GetMapping("/internal/cases")
    @PreAuthorize("hasRole('RESEARCHER')")
    public List<InternalFlowService.CaseView> list(@AuthenticationPrincipal AuthenticatedUser user){return flow.list(user);}
    @GetMapping("/internal/cases/{caseId}")
    @PreAuthorize("hasRole('RESEARCHER') and hasAuthority('ACCOUNT_ENABLED')")
    public InternalFlowService.ResearcherCaseDetail detail(
            @AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID caseId) {
        return flow.detail(user, caseId);
    }
    @PostMapping("/internal/cases/{caseId}/files/{fileId}/download") public InternalFlowService.Download download(@AuthenticationPrincipal AuthenticatedUser user,@PathVariable UUID caseId,@PathVariable UUID fileId){return flow.download(user,caseId,fileId);}
    @PostMapping("/internal/cases/{caseId}/results/{resultId}/download") public InternalFlowService.Download downloadResult(@AuthenticationPrincipal AuthenticatedUser user,@PathVariable UUID caseId,@PathVariable UUID resultId){return flow.downloadResult(user,caseId,resultId);}
    @DeleteMapping("/internal/cases/{caseId}/results/{resultId}")
    @PreAuthorize("hasRole('RESEARCHER') and hasAuthority('ACCOUNT_ENABLED')")
    public ResponseEntity<Void> deleteResult(@AuthenticationPrincipal AuthenticatedUser user,@PathVariable UUID caseId,@PathVariable UUID resultId,@RequestParam String reason){flow.deleteResult(user,caseId,resultId,reason);return ResponseEntity.noContent().build();}
    @PostMapping("/internal/cases/{caseId}/result-uploads")
    @PreAuthorize("hasRole('RESEARCHER') and hasAuthority('ACCOUNT_ENABLED')")
    public ResponseEntity<InternalFlowService.UploadIntent> create(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable UUID caseId,
            @Valid @RequestBody Request request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(
                flow.createResult(user, caseId, request.originalFilename(), request.contentType(), request.sizeBytes()));
    }
    public record Request(@NotBlank String originalFilename,@NotBlank String contentType,@NotNull @Positive Long sizeBytes){}
}
