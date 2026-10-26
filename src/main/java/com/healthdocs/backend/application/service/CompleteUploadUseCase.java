package com.healthdocs.backend.application.service;

import com.healthdocs.backend.application.port.out.AuthProvider.AuthenticatedUser;
import com.healthdocs.backend.application.port.out.CaseRepositoryPort;
import com.healthdocs.backend.domain.model.FileKind;
import com.healthdocs.backend.domain.model.StoredFile;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CompleteUploadUseCase {

    private final CaseRepositoryPort cases;
    private final ConfirmFileUploadUseCase confirmInput;
    private final InternalFlowService internalFlow;

    public CompleteUploadUseCase(
            CaseRepositoryPort cases,
            ConfirmFileUploadUseCase confirmInput,
            InternalFlowService internalFlow) {
        this.cases = cases;
        this.confirmInput = confirmInput;
        this.internalFlow = internalFlow;
    }

    @Transactional
    public Completion execute(AuthenticatedUser user, UUID fileId) {
        StoredFile file = cases.findFileById(fileId)
                .orElseThrow(() -> new ResourceNotFoundException("File not found"));
        if (file.fileKind() == FileKind.INPUT) {
            ConfirmFileUploadUseCase.Confirmation confirmation = confirmInput.executeWithStatus(
                    new ConfirmFileUploadUseCase.Command(user.id(), user.role(), user.enabled(), fileId));
            StoredFile confirmedFile = confirmation.file();
            return new Completion(
                    new InternalFlowService.FileView(
                            confirmedFile.id(), confirmedFile.originalFilename(), confirmedFile.contentType(),
                            confirmedFile.sizeBytes(), confirmedFile.fileKind()),
                    null,
                    confirmation.caseStatus());
        }

        InternalFlowService.Confirmation confirmation = internalFlow.confirm(user, fileId);
        return new Completion(confirmation.file(), confirmation.result(), confirmation.caseStatus());
    }

    public record Completion(
            InternalFlowService.FileView file,
            InternalFlowService.UploadedResult result,
            com.healthdocs.backend.domain.model.CaseStatus caseStatus) { }
}
