package com.healthdocs.backend.adapter.in.web;

import com.healthdocs.backend.application.port.out.AuthProvider.AuthenticatedUser;
import com.healthdocs.backend.application.service.AcceptTermsUseCase;
import com.healthdocs.backend.application.service.ConfirmFileUploadUseCase;
import com.healthdocs.backend.application.service.CreateCaseUseCase;
import com.healthdocs.backend.application.service.GenerateUploadUrlUseCase;
import com.healthdocs.backend.application.service.InternalFlowService;
import com.healthdocs.backend.application.service.ListPatientCasesUseCase;
import com.healthdocs.backend.domain.model.CaseConsent;
import com.healthdocs.backend.domain.model.CaseStatus;
import com.healthdocs.backend.domain.model.FileKind;
import com.healthdocs.backend.domain.model.MedicalCase;
import com.healthdocs.backend.domain.model.StoredFile;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import java.time.Instant;
import java.util.UUID;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class PatientCaseController {

    private final CreateCaseUseCase createCase;
    private final AcceptTermsUseCase acceptTerms;
    private final GenerateUploadUrlUseCase generateUploadUrl;
    private final ConfirmFileUploadUseCase confirmFileUpload;
    private final InternalFlowService internalFlow;
    private final ListPatientCasesUseCase listPatientCases;

    public PatientCaseController(
            CreateCaseUseCase createCase,
            AcceptTermsUseCase acceptTerms,
            GenerateUploadUrlUseCase generateUploadUrl,
            ConfirmFileUploadUseCase confirmFileUpload,
            InternalFlowService internalFlow,
            ListPatientCasesUseCase listPatientCases) {
        this.createCase = createCase;
        this.acceptTerms = acceptTerms;
        this.generateUploadUrl = generateUploadUrl;
        this.confirmFileUpload = confirmFileUpload;
        this.internalFlow = internalFlow;
        this.listPatientCases = listPatientCases;
    }

    @org.springframework.web.bind.annotation.GetMapping("/cases")
    @PreAuthorize("hasRole('PATIENT')")
    public List<ListPatientCasesUseCase.CaseView> listCases(
            @AuthenticationPrincipal AuthenticatedUser user) {
        return listPatientCases.execute(user);
    }

    @PostMapping("/cases")
    @PreAuthorize("hasRole('PATIENT')")
    public ResponseEntity<CaseResponse> createCase(
            @AuthenticationPrincipal AuthenticatedUser user,
            @Valid @RequestBody CreateCaseRequest request) {
        MedicalCase medicalCase = createCase.execute(new CreateCaseUseCase.Command(
                user.id(), user.role(), user.enabled(), request.studyName()));
        return ResponseEntity.status(HttpStatus.CREATED).body(CaseResponse.from(medicalCase));
    }

    @PostMapping("/cases/{caseId}/consent")
    @PreAuthorize("hasRole('PATIENT')")
    public ResponseEntity<ConsentResponse> acceptConsent(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable UUID caseId,
            @Valid @RequestBody AcceptConsentRequest request) {
        CaseConsent consent = acceptTerms.execute(new AcceptTermsUseCase.Command(
                user.id(), user.role(), user.enabled(), caseId, request.termsVersion()));
        return ResponseEntity.status(HttpStatus.CREATED).body(ConsentResponse.from(consent));
    }

    @org.springframework.web.bind.annotation.GetMapping("/cases/{caseId}/result")
    @PreAuthorize("hasRole('PATIENT') and hasAuthority('ACCOUNT_ENABLED')")
    public InternalFlowService.PatientResult getResult(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable UUID caseId) {
        return internalFlow.patientResult(user, caseId);
    }

    @PostMapping("/cases/{caseId}/uploads")
    @PreAuthorize("hasRole('PATIENT')")
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
    public ResponseEntity<?> completeUpload(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable UUID fileId) {
        if (user.role() != com.healthdocs.backend.domain.model.Role.PATIENT) {
            return ResponseEntity.ok(internalFlow.confirm(user, fileId));
        }
        StoredFile file = confirmFileUpload.execute(new ConfirmFileUploadUseCase.Command(
                user.id(), user.role(), user.enabled(), fileId));
        return ResponseEntity.ok(new CompleteUploadResponse(FileResponse.from(file), null, CaseStatus.FILES_RECEIVED));
    }

    public record CreateCaseRequest(@NotBlank @jakarta.validation.constraints.Size(max = 200) String studyName) {
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
            Instant createdAt) {

        static CaseResponse from(MedicalCase medicalCase) {
            return new CaseResponse(
                    medicalCase.id(),
                    medicalCase.referenceCode(),
                    medicalCase.status(),
                    medicalCase.studyName(),
                    medicalCase.createdAt());
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

    public record CompleteUploadResponse(FileResponse file, Object result, CaseStatus caseStatus) {
    }
}
