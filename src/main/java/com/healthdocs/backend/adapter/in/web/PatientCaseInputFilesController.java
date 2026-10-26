package com.healthdocs.backend.adapter.in.web;

import com.healthdocs.backend.adapter.out.persistence.MedicalCaseRepository;
import com.healthdocs.backend.adapter.out.persistence.InputChangeRequestFileRepository;
import com.healthdocs.backend.adapter.out.persistence.InputChangeRequestRepository;
import com.healthdocs.backend.adapter.out.persistence.StoredFileRepository;
import com.healthdocs.backend.adapter.out.persistence.entity.StoredFileEntity;
import com.healthdocs.backend.application.port.out.AuthProvider.AuthenticatedUser;
import com.healthdocs.backend.application.port.out.StoragePort;
import com.healthdocs.backend.application.service.ResourceNotFoundException;
import com.healthdocs.backend.domain.model.FileKind;
import com.healthdocs.backend.domain.model.InputChangeFileSet;
import com.healthdocs.backend.domain.model.InputChangeRequestStatus;
import com.healthdocs.backend.infrastructure.config.InternalFlowProperties;
import java.util.List;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

@RestController
@RequestMapping("/api/v1/cases/{caseId}/uploads")
@PreAuthorize("hasRole('PATIENT') and hasAuthority('ACCOUNT_ENABLED')")
public class PatientCaseInputFilesController {
    private final MedicalCaseRepository cases;
    private final StoredFileRepository files;
    private final InputChangeRequestRepository requests;
    private final InputChangeRequestFileRepository requestFiles;
    private final StoragePort storage;
    private final InternalFlowProperties properties;
    private com.healthdocs.backend.application.port.out.SecurityAuditEventPort securityAudit;

    public PatientCaseInputFilesController(MedicalCaseRepository cases, StoredFileRepository files,
            InputChangeRequestRepository requests, InputChangeRequestFileRepository requestFiles,
            StoragePort storage, InternalFlowProperties properties) {
        this.cases = cases;
        this.files = files;
        this.requests = requests;
        this.requestFiles = requestFiles;
        this.storage = storage;
        this.properties = properties;
    }

    @Autowired
    public void setSecurityAuditEventPort(
            com.healthdocs.backend.application.port.out.SecurityAuditEventPort securityAudit) {
        this.securityAudit = securityAudit;
    }

    @GetMapping
    public List<FileView> list(@AuthenticationPrincipal AuthenticatedUser owner, @PathVariable UUID caseId) {
        if (!cases.existsByIdAndPatientId(caseId, owner.id())) {
            throw new ResourceNotFoundException("Case not found");
        }
        return files.findByMedicalCaseIdAndFileKindAndInputActiveTrue(caseId, FileKind.INPUT).stream()
                .filter(file -> file.getConfirmedAt() != null)
                .map(FileView::from)
                .toList();
    }

    @PostMapping("/{fileId}/download")
    @Transactional
    public DownloadIntent createDownload(@AuthenticationPrincipal AuthenticatedUser owner,
            @PathVariable UUID caseId, @PathVariable UUID fileId) {
        if (!cases.existsByIdAndPatientId(caseId, owner.id())) {
            throw new ResourceNotFoundException("File not found");
        }
        StoredFileEntity file = files.findById(fileId)
                .filter(candidate -> candidate.getMedicalCase().getId().equals(caseId)
                        && candidate.getFileKind() == FileKind.INPUT
                        && candidate.getConfirmedAt() != null)
                .orElseThrow(() -> new ResourceNotFoundException("File not found"));
        boolean currentFile = file.isInputActive();
        boolean activeProposal = !currentFile && requests
                .findFirstByMedicalCase_IdAndRequestedBy_IdAndStatusIn(caseId, owner.id(), List.of(
                        InputChangeRequestStatus.DRAFT, InputChangeRequestStatus.PENDING_REVIEW))
                .flatMap(request -> requestFiles.findFirstByRequest_IdAndFile_IdAndFileSetAndRemovedAtIsNull(
                        request.getId(), fileId, InputChangeFileSet.PROPOSED))
                .isPresent();
        if (!currentFile && !activeProposal) {
            throw new ResourceNotFoundException("File not found");
        }
        if (!storage.objectExists(file.getObjectKey())) {
            throw new ResourceNotFoundException("File not found");
        }
        StoragePort.UploadUrlResult url = storage.generateDownloadUrl(file.getObjectKey(),
                file.getContentType(), properties.downloadTtlMinutes());
        if (securityAudit != null) {
            securityAudit.record("SENSITIVE_RESOURCE_ACCESS_GRANTED", java.time.Instant.now(), owner.id(),
                    owner.id(), caseId, "INPUT_FILE", fileId, "DOWNLOAD_URL_ISSUED", null, null, null,
                    "SENSITIVE_RESOURCE_ACCESS_GRANTED:" + UUID.randomUUID());
        }
        return new DownloadIntent(url.uploadUrl(), url.expiresAt());
    }

    public record FileView(UUID id, String originalFilename, String contentType, Long sizeBytes, FileKind fileKind) {
        static FileView from(StoredFileEntity file) {
            return new FileView(file.getId(), file.getOriginalFilename(), file.getContentType(),
                    file.getSizeBytes(), file.getFileKind());
        }
    }

    public record DownloadIntent(String downloadUrl, java.time.Instant expiresAt) { }
}
