package com.healthdocs.backend.application.service;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.*;

import com.healthdocs.backend.adapter.out.persistence.*;
import com.healthdocs.backend.adapter.out.persistence.entity.*;
import com.healthdocs.backend.application.port.out.AuthProvider.AuthenticatedUser;
import com.healthdocs.backend.application.port.out.StoragePort;
import com.healthdocs.backend.domain.model.*;
import com.healthdocs.backend.infrastructure.config.InternalFlowProperties;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class InputChangeReviewServiceTest {
    private final InputChangeRequestRepository requests = mock(InputChangeRequestRepository.class);
    private final InputChangeRequestFileRepository requestFiles = mock(InputChangeRequestFileRepository.class);
    private final MedicalCaseRepository cases = mock(MedicalCaseRepository.class);
    private final StoredFileRepository files = mock(StoredFileRepository.class);
    private final AppUserRepository users = mock(AppUserRepository.class);
    private final CaseResultRepository results = mock(CaseResultRepository.class);
    private final InputChangeFileCleanupRepository cleanup = mock(InputChangeFileCleanupRepository.class);
    private final InputChangeDraftService draftViews = mock(InputChangeDraftService.class);
    private final StoragePort storage = mock(StoragePort.class);
    private final InternalFlowProperties properties = mock(InternalFlowProperties.class);
    private final Clock clock = Clock.fixed(Instant.parse("2026-10-15T12:00:00Z"), ZoneOffset.UTC);
    private final InputChangeReviewService service = new InputChangeReviewService(requests, requestFiles, cases,
            files, users, results, cleanup, draftViews, storage, properties, clock);
    private final UUID coordinatorId = UUID.randomUUID();
    private final UUID ownerId = UUID.randomUUID();
    private final UUID caseId = UUID.randomUUID();
    private final UUID requestId = UUID.randomUUID();
    private final AuthenticatedUser coordinator = new AuthenticatedUser(coordinatorId, "coord@test.local",
            Role.COORDINATOR, true);
    private MedicalCaseEntity medicalCase;
    private InputChangeRequestEntity request;
    private AppUserEntity ownerEntity;
    private AppUserEntity coordinatorEntity;

    @BeforeEach
    void setUp() {
        medicalCase = mock(MedicalCaseEntity.class);
        when(medicalCase.getId()).thenReturn(caseId);
        request = mock(InputChangeRequestEntity.class);
        when(request.getId()).thenReturn(requestId);
        when(request.getMedicalCase()).thenReturn(medicalCase);
        when(request.getStatus()).thenReturn(InputChangeRequestStatus.PENDING_REVIEW);
        when(request.getResumeCaseStatus()).thenReturn(CaseStatus.FILES_RECEIVED);
        ownerEntity = mock(AppUserEntity.class);
        when(ownerEntity.getId()).thenReturn(ownerId);
        coordinatorEntity = mock(AppUserEntity.class);
        when(coordinatorEntity.getId()).thenReturn(coordinatorId);
        when(request.getRequestedBy()).thenReturn(ownerEntity);
    }

    @Test
    void approvalActivatesProposalAndKeepsReplacedFilesAsHistory() {
        prepareDecision();
        when(request.getResumeCaseStatus()).thenReturn(CaseStatus.ENABLED_FOR_PROCESSING);
        StoredFileEntity current = inputFile("old.pdf", true);
        StoredFileEntity proposed = inputFile("new.pdf", false);
        InputChangeRequestFileEntity proposedMembership = membership(proposed);
        when(requestFiles.findAllByRequest_IdAndFileSetAndRemovedAtIsNullOrderByAddedAtAsc(
                requestId, InputChangeFileSet.PROPOSED)).thenReturn(List.of(proposedMembership));
        when(files.findByMedicalCaseIdAndFileKindAndInputActiveTrue(caseId, FileKind.INPUT))
                .thenReturn(List.of(current));
        when(files.findByIdForUpdate(current.getId())).thenReturn(Optional.of(current));
        when(files.findByIdForUpdate(proposed.getId())).thenReturn(Optional.of(proposed));
        when(users.findById(coordinatorId)).thenReturn(Optional.of(coordinatorEntity));

        service.approve(coordinator, requestId);

        verify(current).stageInput();
        verify(current, never()).activateInput();
        verify(proposed).activateInput();
        verify(cleanup, never()).save(any());
        verify(request).decide(true, coordinatorEntity, null, clock.instant());
        verify(medicalCase).setStatus(CaseStatus.ENABLED_FOR_PROCESSING);
        verify(requests).saveAndFlush(request);
        verify(cases).saveAndFlush(medicalCase);
    }

    @Test
    void rejectionKeepsOriginalAndQueuesExclusiveProposalForCleanup() {
        prepareDecision();
        when(request.getResumeCaseStatus()).thenReturn(CaseStatus.ENABLED_FOR_PROCESSING);
        StoredFileEntity staged = inputFile("proposed.pdf", false);
        InputChangeRequestFileEntity proposedMembership = membership(staged);
        when(requestFiles.findAllByRequest_IdAndFileSetAndRemovedAtIsNullOrderByAddedAtAsc(
                requestId, InputChangeFileSet.PROPOSED)).thenReturn(List.of(proposedMembership));
        when(cleanup.existsById(staged.getId())).thenReturn(false);
        when(users.findById(coordinatorId)).thenReturn(Optional.of(coordinatorEntity));

        service.reject(coordinator, requestId, "El archivo no corresponde al estudio");

        verify(request).decide(false, coordinatorEntity, "El archivo no corresponde al estudio", clock.instant());
        verify(cleanup).save(any(InputChangeFileCleanupEntity.class));
        verify(files, never()).findByMedicalCaseIdAndFileKindAndInputActiveTrue(caseId, FileKind.INPUT);
        verify(medicalCase).setStatus(CaseStatus.ENABLED_FOR_PROCESSING);
    }

    @Test
    void coordinatorCannotDecideTheirOwnRequest() {
        when(requests.findCaseIdById(requestId)).thenReturn(Optional.of(caseId));
        when(cases.findByIdForUpdate(caseId)).thenReturn(Optional.of(medicalCase));
        when(requests.findByIdForUpdate(requestId)).thenReturn(Optional.of(request));
        when(request.getRequestedBy()).thenReturn(coordinatorEntity);
        when(coordinatorEntity.getId()).thenReturn(coordinatorId);

        assertThrows(ForbiddenOperationException.class, () -> service.approve(coordinator, requestId));

        verify(request, never()).decide(anyBoolean(), any(AppUserEntity.class), isNull(), any(Instant.class));
    }

    @Test
    void decisionCannotBeRepeatedAfterRequestLeavesReview() {
        when(requests.findCaseIdById(requestId)).thenReturn(Optional.of(caseId));
        when(cases.findByIdForUpdate(caseId)).thenReturn(Optional.of(medicalCase));
        when(requests.findByIdForUpdate(requestId)).thenReturn(Optional.of(request));
        when(medicalCase.getStatus()).thenReturn(CaseStatus.FILES_RECEIVED);
        when(request.getStatus()).thenReturn(InputChangeRequestStatus.APPROVED);

        assertThrows(BusinessRuleViolationException.class, () -> service.approve(coordinator, requestId));

        verify(request, never()).decide(anyBoolean(), any(AppUserEntity.class), isNull(), any(Instant.class));
        verify(medicalCase, never()).setStatus(any());
    }

    @Test
    void reviewRequiresCoordinatorRole() {
        AuthenticatedUser researcher = new AuthenticatedUser(UUID.randomUUID(), "researcher@test.local",
                Role.RESEARCHER, true);

        assertThrows(ForbiddenOperationException.class, () -> service.listPending(researcher));

        verifyNoInteractions(requests);
    }

    private void prepareDecision() {
        when(requests.findCaseIdById(requestId)).thenReturn(Optional.of(caseId));
        when(cases.findByIdForUpdate(caseId)).thenReturn(Optional.of(medicalCase));
        when(requests.findByIdForUpdate(requestId)).thenReturn(Optional.of(request));
        when(medicalCase.getStatus()).thenReturn(CaseStatus.INPUT_CHANGE_REVIEW);
        when(results.existsByMedicalCaseId(caseId)).thenReturn(false);
    }

    private StoredFileEntity inputFile(String name, boolean active) {
        UUID fileId = UUID.randomUUID();
        StoredFileEntity file = mock(StoredFileEntity.class);
        when(file.getId()).thenReturn(fileId);
        when(file.getFileKind()).thenReturn(FileKind.INPUT);
        when(file.getMedicalCase()).thenReturn(medicalCase);
        when(file.getConfirmedAt()).thenReturn(Instant.parse("2026-10-15T11:00:00Z"));
        when(file.getObjectKey()).thenReturn("input/" + name);
        when(file.isInputActive()).thenReturn(active);
        when(storage.objectExists("input/" + name)).thenReturn(true);
        return file;
    }

    private InputChangeRequestFileEntity membership(StoredFileEntity file) {
        InputChangeRequestFileEntity membership = mock(InputChangeRequestFileEntity.class);
        when(membership.getFile()).thenReturn(file);
        return membership;
    }
}
