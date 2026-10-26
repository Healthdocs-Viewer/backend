package com.healthdocs.backend.adapter.in.web;

import com.healthdocs.backend.application.port.out.AuthProvider.AuthenticatedUser;
import com.healthdocs.backend.application.service.AcceptTermsUseCase;
import com.healthdocs.backend.application.service.CompleteUploadUseCase;
import com.healthdocs.backend.application.service.CreateCaseUseCase;
import com.healthdocs.backend.application.service.GenerateUploadUrlUseCase;
import com.healthdocs.backend.application.service.GetPatientCaseDetailsUseCase;
import com.healthdocs.backend.application.service.InternalFlowService;
import com.healthdocs.backend.application.service.ListPatientCasesUseCase;
import com.healthdocs.backend.application.service.SubmitCaseUseCase;
import com.healthdocs.backend.domain.model.CaseConsent;
import com.healthdocs.backend.domain.model.CaseStatus;
import com.healthdocs.backend.domain.model.FileKind;
import com.healthdocs.backend.domain.model.MedicalCase;
import com.healthdocs.backend.domain.model.StoredFile;
import com.healthdocs.backend.domain.model.ResultPublicationType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import java.util.List;
import com.fasterxml.jackson.annotation.JsonInclude;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class PatientCaseController {

    private final CreateCaseUseCase createCase;
    private final AcceptTermsUseCase acceptTerms;
    private final GenerateUploadUrlUseCase generateUploadUrl;
    private final CompleteUploadUseCase completeUpload;
    private final SubmitCaseUseCase submitCase;
    private final InternalFlowService internalFlow;
    private final ListPatientCasesUseCase listPatientCases;
    private final GetPatientCaseDetailsUseCase getCaseDetails;

    public PatientCaseController(
            CreateCaseUseCase createCase,
            AcceptTermsUseCase acceptTerms,
            GenerateUploadUrlUseCase generateUploadUrl,
            CompleteUploadUseCase completeUpload,
            SubmitCaseUseCase submitCase,
            InternalFlowService internalFlow,
            ListPatientCasesUseCase listPatientCases,
            GetPatientCaseDetailsUseCase getCaseDetails) {
        this.createCase = createCase;
        this.acceptTerms = acceptTerms;
        this.generateUploadUrl = generateUploadUrl;
        this.completeUpload = completeUpload;
        this.submitCase = submitCase;
        this.internalFlow = internalFlow;
        this.listPatientCases = listPatientCases;
        this.getCaseDetails = getCaseDetails;
    }

    @org.springframework.web.bind.annotation.GetMapping("/cases")
    @PreAuthorize("hasAnyRole('PATIENT', 'RESEARCHER', 'COORDINATOR', 'ADMIN') and hasAuthority('ACCOUNT_ENABLED')")
    public ListPatientCasesUseCase.Response listCases(
            @AuthenticationPrincipal AuthenticatedUser user,
            @org.springframework.web.bind.annotation.RequestParam(defaultValue = "") String q,
            @org.springframework.web.bind.annotation.RequestParam(defaultValue = "ALL") String status,
            @org.springframework.web.bind.annotation.RequestParam(defaultValue = "createdAt:desc") String sort,
            @org.springframework.web.bind.annotation.RequestParam(defaultValue = "0") int page,
            @org.springframework.web.bind.annotation.RequestParam(defaultValue = "10") int size) {
        return listPatientCases.execute(user, q, status, sort, page, size);
    }

    @PostMapping("/cases")
    @PreAuthorize("hasAnyRole('PATIENT', 'RESEARCHER', 'COORDINATOR', 'ADMIN') and hasAuthority('ACCOUNT_ENABLED')")
    public ResponseEntity<CaseResponse> createCase(
            @AuthenticationPrincipal AuthenticatedUser user,
            @RequestHeader("Idempotency-Key") UUID idempotencyKey,
            @Valid @RequestBody CreateCaseRequest request) {
        CreateCaseUseCase.CreationResult result = createCase.executeIdempotent(new CreateCaseUseCase.Command(
                user.id(), user.role(), user.enabled(), request.studyName(), request.additionalReference(),
                request.originInstitution(), request.studyDate(), request.notes(), idempotencyKey));
        return ResponseEntity.status(result.replayed() ? HttpStatus.OK : HttpStatus.CREATED)
                .header("Idempotency-Replayed", Boolean.toString(result.replayed()))
                .body(CaseResponse.from(result.medicalCase()));
    }

    @org.springframework.web.bind.annotation.GetMapping("/cases/{caseId}")
    @PreAuthorize("hasAnyRole('PATIENT', 'RESEARCHER', 'COORDINATOR', 'ADMIN') and hasAuthority('ACCOUNT_ENABLED')")
    public GetPatientCaseDetailsUseCase.CaseDetails getCaseDetails(
            @AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID caseId) {
        return getCaseDetails.execute(user, caseId);
    }

    @PostMapping("/cases/{caseId}/consent")
    @PreAuthorize("hasAnyRole('PATIENT', 'RESEARCHER', 'COORDINATOR', 'ADMIN') and hasAuthority('ACCOUNT_ENABLED')")
    public ResponseEntity<ConsentResponse> acceptConsent(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable UUID caseId,
            @Valid @RequestBody AcceptConsentRequest request) {
        CaseConsent consent = acceptTerms.execute(new AcceptTermsUseCase.Command(
                user.id(), user.role(), user.enabled(), caseId, request.termsVersion(), request.acceptedAt()));
        return ResponseEntity.status(HttpStatus.CREATED).body(ConsentResponse.from(consent));
    }

    @PostMapping("/cases/{caseId}/submission")
    @PreAuthorize("hasAnyRole('PATIENT', 'RESEARCHER', 'COORDINATOR', 'ADMIN') and hasAuthority('ACCOUNT_ENABLED')")
    public ResponseEntity<Void> submitCase(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable UUID caseId) {
        submitCase.execute(new SubmitCaseUseCase.Command(user.id(), user.role(), user.enabled(), caseId));
        return ResponseEntity.noContent().build();
    }

    @org.springframework.web.bind.annotation.GetMapping("/cases/{caseId}/result")
    @PreAuthorize("hasRole('PATIENT') and hasAuthority('ACCOUNT_ENABLED')")
    public InternalFlowService.PatientResult getResult(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable UUID caseId) {
        return internalFlow.patientResult(user, caseId);
    }

    @org.springframework.web.bind.annotation.GetMapping("/patient/results")
    @PreAuthorize("hasRole('PATIENT') and hasAuthority('ACCOUNT_ENABLED')")
    public InternalFlowService.PatientResults listResults(
            @AuthenticationPrincipal AuthenticatedUser user,
            @org.springframework.web.bind.annotation.RequestParam(defaultValue = "") String q,
            @org.springframework.web.bind.annotation.RequestParam(defaultValue = "availableAt,desc") String sort,
            @org.springframework.web.bind.annotation.RequestParam(defaultValue = "0") int page,
            @org.springframework.web.bind.annotation.RequestParam(defaultValue = "10") int size,
            @org.springframework.web.bind.annotation.RequestParam(required = false) ResultPublicationType publicationType) {
        return internalFlow.patientResults(user, q, sort, page, size, publicationType);
    }

    @PostMapping("/cases/{caseId}/uploads")
    @PreAuthorize("hasAnyRole('PATIENT', 'RESEARCHER', 'COORDINATOR', 'ADMIN') and hasAuthority('ACCOUNT_ENABLED')")
    public ResponseEntity<UploadIntentResponse> generateUploadUrl(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable UUID caseId,
            @Valid @RequestBody GenerateUploadUrlRequest request) {
        GenerateUploadUrlUseCase.Result result = generateUploadUrl.execute(new GenerateUploadUrlUseCase.Command(
                user.id(),
                user.role(),
                user.enabled(),
                caseId,
                request.originalFilename(),
                request.contentType(),
                request.sizeBytes()));
        return ResponseEntity.status(HttpStatus.CREATED).body(UploadIntentResponse.from(result));
    }

    @PostMapping("/files/{fileId}/complete")
    @PreAuthorize("hasAnyRole('PATIENT', 'RESEARCHER', 'COORDINATOR', 'ADMIN') and hasAuthority('ACCOUNT_ENABLED')")
    public ResponseEntity<?> completeUpload(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable UUID fileId) {
        CompleteUploadUseCase.Completion completion = completeUpload.execute(user, fileId);
        InternalFlowService.FileView file = completion.file();
        return ResponseEntity.ok(new CompleteUploadResponse(
                new FileResponse(file.id(), file.originalFilename(), file.contentType(), file.sizeBytes(), file.fileKind()),
                completion.result(), completion.caseStatus()));
    }

    public record CreateCaseRequest(
            @NotBlank @jakarta.validation.constraints.Size(max = 200) String studyName,
            @jakarta.validation.constraints.Size(max = 100) String additionalReference,
            @NotBlank @jakarta.validation.constraints.Size(max = 200) String originInstitution,
            @jakarta.validation.constraints.NotNull LocalDate studyDate,
            @jakarta.validation.constraints.Size(max = 500) String notes) {
    }

    public record AcceptConsentRequest(@NotBlank String termsVersion, Instant acceptedAt) {
    }

    public record GenerateUploadUrlRequest(
            @NotBlank String originalFilename,
            @NotBlank String contentType,
            @Positive Long sizeBytes) {
    }

    public record CaseResponse(
            UUID id,
            String referenceCode,
            CaseStatus status,
            String studyName,
            String additionalReference,
            String originInstitution,
            LocalDate studyDate,
            String notes,
            Instant createdAt,
            Instant updatedAt,
            UUID availableResultId) {

        static CaseResponse from(MedicalCase medicalCase) {
            return new CaseResponse(
                    medicalCase.id(),
                    medicalCase.referenceCode(),
                    medicalCase.status(),
                    medicalCase.studyName(),
                    medicalCase.additionalReference(), medicalCase.originInstitution(), medicalCase.studyDate(), medicalCase.notes(),
                    medicalCase.createdAt(), medicalCase.updatedAt(), null);
        }
    }

    public record ConsentResponse(UUID id, UUID caseId, String termsVersion, Instant acceptedAt) {

        static ConsentResponse from(CaseConsent consent) {
            return new ConsentResponse(
                    consent.id(), consent.caseId(), consent.termsVersion(), consent.acceptedAt());
        }
    }

    public record FileResponse(
            UUID id,
            String originalFilename,
            String contentType,
            Long sizeBytes,
            FileKind fileKind) {

        static FileResponse from(StoredFile file) {
            return new FileResponse(
                    file.id(), file.originalFilename(), file.contentType(), file.sizeBytes(), file.fileKind());
        }
    }

    public record UploadIntentResponse(FileResponse file, String uploadUrl, Instant expiresAt) {

        static UploadIntentResponse from(GenerateUploadUrlUseCase.Result result) {
            return new UploadIntentResponse(
                    FileResponse.from(result.file()), result.uploadUrl(), result.expiresAt());
        }
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record CompleteUploadResponse(FileResponse file, Object result, CaseStatus caseStatus) {
    }
}
