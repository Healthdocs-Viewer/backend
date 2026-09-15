package com.healthdocs.backend.adapter.in.web;

import com.healthdocs.backend.application.port.out.AuthProvider.AuthenticatedUser;
import com.healthdocs.backend.application.service.AcceptTermsUseCase;
import com.healthdocs.backend.application.service.ConfirmFileUploadUseCase;
import com.healthdocs.backend.application.service.CreateCaseUseCase;
import com.healthdocs.backend.application.service.GenerateUploadUrlUseCase;
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

    public PatientCaseController(
            CreateCaseUseCase createCase,
            AcceptTermsUseCase acceptTerms,
            GenerateUploadUrlUseCase generateUploadUrl,
            ConfirmFileUploadUseCase confirmFileUpload) {
        this.createCase = createCase;
        this.acceptTerms = acceptTerms;
        this.generateUploadUrl = generateUploadUrl;
        this.confirmFileUpload = confirmFileUpload;
    }

    @PostMapping("/cases")
    @PreAuthorize("hasRole('PACIENTE')")
    public ResponseEntity<CaseResponse> createCase(
            @AuthenticationPrincipal AuthenticatedUser user,
            @Valid @RequestBody CreateCaseRequest request) {
        MedicalCase medicalCase = createCase.execute(new CreateCaseUseCase.Command(
                user.id(), user.role(), user.enabled(), request.studyType()));
        return ResponseEntity.status(HttpStatus.CREATED).body(CaseResponse.from(medicalCase));
    }

    @PostMapping("/cases/{caseId}/consent")
    @PreAuthorize("hasRole('PACIENTE')")
    public ResponseEntity<ConsentResponse> acceptConsent(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable UUID caseId,
            @Valid @RequestBody AcceptConsentRequest request) {
        CaseConsent consent = acceptTerms.execute(new AcceptTermsUseCase.Command(
                user.id(), user.role(), user.enabled(), caseId, request.termsVersion()));
        return ResponseEntity.status(HttpStatus.CREATED).body(ConsentResponse.from(consent));
    }

    @PostMapping("/cases/{caseId}/uploads")
    @PreAuthorize("hasRole('PACIENTE')")
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
    @PreAuthorize("hasRole('PACIENTE')")
    public ResponseEntity<FileResponse> completeUpload(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable UUID fileId) {
        StoredFile file = confirmFileUpload.execute(new ConfirmFileUploadUseCase.Command(
                user.id(), user.role(), user.enabled(), fileId));
        return ResponseEntity.ok(FileResponse.from(file));
    }

    public record CreateCaseRequest(@NotBlank String studyType) {
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
            String studyType,
            Instant createdAt) {

        static CaseResponse from(MedicalCase medicalCase) {
            return new CaseResponse(
                    medicalCase.id(),
                    medicalCase.referenceCode(),
                    medicalCase.status(),
                    medicalCase.studyType(),
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
}
