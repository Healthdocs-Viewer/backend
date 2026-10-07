package com.healthdocs.backend.adapter.in.web;

import com.healthdocs.backend.application.port.out.AuthProvider.AuthenticatedUser;
import com.healthdocs.backend.application.service.InternalFlowService;
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
    public InternalCaseController(InternalFlowService flow){this.flow=flow;}
    @GetMapping("/internal/cases") public List<InternalFlowService.CaseView> list(@AuthenticationPrincipal AuthenticatedUser user){return flow.list(user);}
    @PostMapping("/internal/cases/{caseId}/files/{fileId}/download") public InternalFlowService.Download download(@AuthenticationPrincipal AuthenticatedUser user,@PathVariable UUID caseId,@PathVariable UUID fileId){return flow.download(user,caseId,fileId);}
    @PostMapping("/internal/cases/{caseId}/results/{resultId}/download") public InternalFlowService.Download downloadResult(@AuthenticationPrincipal AuthenticatedUser user,@PathVariable UUID caseId,@PathVariable UUID resultId){return flow.downloadResult(user,caseId,resultId);}
    @DeleteMapping("/internal/cases/{caseId}/results/{resultId}") public ResponseEntity<Void> deleteResult(@AuthenticationPrincipal AuthenticatedUser user,@PathVariable UUID caseId,@PathVariable UUID resultId){flow.deleteResult(user,caseId,resultId);return ResponseEntity.noContent().build();}
    @PostMapping("/internal/cases/{caseId}/result-uploads") public ResponseEntity<InternalFlowService.UploadIntent> create(@AuthenticationPrincipal AuthenticatedUser user,@PathVariable UUID caseId,@Valid @RequestBody Request request){return ResponseEntity.status(HttpStatus.CREATED).body(flow.createResult(user,caseId,request.originalFilename(),request.contentType(),request.sizeBytes()));}
    public record Request(@NotBlank String originalFilename,@NotBlank String contentType,@NotNull @Positive Long sizeBytes){}
}
