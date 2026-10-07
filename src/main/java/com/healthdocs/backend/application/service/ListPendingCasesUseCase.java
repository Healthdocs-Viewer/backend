package com.healthdocs.backend.application.service;

import com.healthdocs.backend.adapter.out.persistence.MedicalCaseRepository;
import com.healthdocs.backend.adapter.out.persistence.StoredFileRepository;
import com.healthdocs.backend.adapter.out.persistence.entity.MedicalCaseEntity;
import com.healthdocs.backend.application.port.out.AuthProvider.AuthenticatedUser;
import com.healthdocs.backend.domain.model.CaseStatus;
import com.healthdocs.backend.domain.model.FileKind;
import com.healthdocs.backend.domain.model.Role;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ListPendingCasesUseCase {

    private final MedicalCaseRepository cases;
    private final StoredFileRepository files;

    public ListPendingCasesUseCase(MedicalCaseRepository cases, StoredFileRepository files) {
        this.cases = cases;
        this.files = files;
    }

    @Transactional(readOnly = true)
    public List<PendingCaseView> execute(AuthenticatedUser coordinator) {
        if (coordinator == null || !coordinator.enabled() || coordinator.role() != Role.COORDINATOR) {
            throw new ForbiddenOperationException("Only enabled coordinators can list pending cases");
        }
        return cases.findPendingCases(CaseStatus.FILES_RECEIVED).stream()
                .map(this::toView)
                .toList();
    }

    private PendingCaseView toView(MedicalCaseEntity c) {
        List<FileView> fileViews = files.findByMedicalCaseId(c.getId()).stream()
                .filter(f -> f.getFileKind() == FileKind.INPUT)
                .map(f -> new FileView(
                        f.getId(),
                        f.getOriginalFilename(),
                        f.getContentType(),
                        f.getSizeBytes(),
                        f.getFileKind()))
                .toList();
        return new PendingCaseView(
                c.getId(),
                c.getReferenceCode(),
                c.getStudyName(),
                c.getStatus(),
                c.getCreatedAt(),
                fileViews);
    }

    public record FileView(
            UUID id,
            String originalFilename,
            String contentType,
            Long sizeBytes,
            FileKind fileKind) {
    }

    public record PendingCaseView(
            UUID id,
            String referenceCode,
            String studyName,
            CaseStatus status,
            Instant createdAt,
            List<FileView> files) {
    }
}
