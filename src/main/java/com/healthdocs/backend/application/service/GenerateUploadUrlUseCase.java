package com.healthdocs.backend.application.service;

import com.healthdocs.backend.application.port.out.CaseRepositoryPort;
import com.healthdocs.backend.application.port.out.StoragePort;
import com.healthdocs.backend.domain.model.FileKind;
import com.healthdocs.backend.domain.model.MedicalCase;
import com.healthdocs.backend.domain.model.Role;
import com.healthdocs.backend.domain.model.StoredFile;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class GenerateUploadUrlUseCase {

    private static final int UPLOAD_TTL_MINUTES = 15;

    private final CaseRepositoryPort cases;
    private final StoragePort storage;
    private final Clock clock;

    public GenerateUploadUrlUseCase(CaseRepositoryPort cases, StoragePort storage, Clock clock) {
        this.cases = cases;
        this.storage = storage;
        this.clock = clock;
    }

    @Transactional
    public Result execute(Command command) {
        AcceptTermsUseCase.requireEnabledCaseActor(command.patientId(), command.role(), command.enabled());
        String originalFilename = requireText(command.originalFilename(), "originalFilename");
        String contentType = requireText(command.contentType(), "contentType");
        if (command.sizeBytes() != null && command.sizeBytes() < 1) {
            throw new IllegalArgumentException("sizeBytes must be positive");
        }
        MedicalCase medicalCase = cases.findCaseByIdForUpdate(command.caseId())
                .orElseThrow(() -> new ResourceNotFoundException("Case not found"));
        AcceptTermsUseCase.requireOwnership(medicalCase, command.patientId());
        if (medicalCase.status() != com.healthdocs.backend.domain.model.CaseStatus.FILE_PENDING
                && medicalCase.status() != com.healthdocs.backend.domain.model.CaseStatus.CONFIRMATION_PENDING) {
            throw new BusinessRuleViolationException("Case is not waiting for an input file");
        }
        if (!cases.hasAnyConsent(command.caseId())) {
            throw new BusinessRuleViolationException("Consent is required before requesting an upload URL");
        }
        Instant now = clock.instant();
        if (cases.findActiveInputUploadIntent(command.caseId(), now).isPresent()) {
            throw new BusinessRuleViolationException("An input upload intent is already active for this case");
        }

        UUID fileId = UUID.randomUUID();
        String objectKey = "input/%s/%s/%s".formatted(command.caseId(), fileId, sanitizeFilename(originalFilename));
        StoragePort.UploadUrlResult uploadUrl = storage.generateUploadUrl(objectKey, contentType, UPLOAD_TTL_MINUTES);
        if (uploadUrl.expiresAt() == null || !now.isBefore(uploadUrl.expiresAt())) {
            throw new IllegalStateException("Storage provider returned an invalid upload URL expiration");
        }
        StoredFile file = new StoredFile(
                fileId,
                command.caseId(),
                objectKey,
                FileKind.INPUT,
                originalFilename,
                contentType,
                command.sizeBytes(),
                now,
                now,
                command.patientId(),
                uploadUrl.expiresAt());
        StoredFile saved = cases.saveFile(file);
        return new Result(saved, uploadUrl.uploadUrl(), uploadUrl.expiresAt());
    }

    private static String sanitizeFilename(String filename) {
        return filename.replace("\\", "_").replace("/", "_").trim();
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
        return value.trim();
    }

    public record Command(
            UUID patientId,
            Role role,
            boolean enabled,
            UUID caseId,
            String originalFilename,
            String contentType,
            Long sizeBytes) {
    }

    public record Result(StoredFile file, String uploadUrl, Instant expiresAt) {
    }
}
