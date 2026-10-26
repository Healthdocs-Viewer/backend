package com.healthdocs.backend.application.service;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.healthdocs.backend.adapter.out.persistence.*;
import com.healthdocs.backend.adapter.out.persistence.entity.*;
import com.healthdocs.backend.application.port.out.AuthProvider.AuthenticatedUser;
import com.healthdocs.backend.application.port.out.StoragePort;
import com.healthdocs.backend.domain.model.*;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class InputChangeDraftServiceTest {
    private final MedicalCaseRepository cases = mock(MedicalCaseRepository.class);
    private final StoredFileRepository files = mock(StoredFileRepository.class);
    private final AppUserRepository users = mock(AppUserRepository.class);
    private final CaseResultRepository results = mock(CaseResultRepository.class);
    private final InputChangeRequestRepository requests = mock(InputChangeRequestRepository.class);
    private final InputChangeRequestFileRepository requestFiles = mock(InputChangeRequestFileRepository.class);
    private final InputChangeFileCleanupRepository cleanup = mock(InputChangeFileCleanupRepository.class);
    private final StoragePort storage = mock(StoragePort.class);
    private final Clock clock = Clock.fixed(Instant.parse("2026-10-15T12:00:00Z"), ZoneOffset.UTC);
    private final InputChangeDraftService service = new InputChangeDraftService(cases, files, users, results,
            requests, requestFiles, cleanup, storage, clock);
    private final UUID ownerId = UUID.randomUUID();
    private final UUID caseId = UUID.randomUUID();
    private final UUID requestId = UUID.randomUUID();
    private final AuthenticatedUser owner = new AuthenticatedUser(ownerId, "owner@test.local", Role.PATIENT, true);
    private MedicalCaseEntity medicalCase;
    private InputChangeRequestEntity request;

    @BeforeEach
    void setUp() {
        medicalCase = mock(MedicalCaseEntity.class);
        when(medicalCase.getId()).thenReturn(caseId);
        when(medicalCase.getPatient()).thenReturn(mock(AppUserEntity.class));
        when(medicalCase.getPatient().getId()).thenReturn(ownerId);
        request = mock(InputChangeRequestEntity.class);
        when(request.getId()).thenReturn(requestId);
        when(request.getMedicalCase()).thenReturn(medicalCase);
        when(request.getStatus()).thenReturn(InputChangeRequestStatus.DRAFT);
    }

    @Test
    void onlyThePatientOwnerCanSubmit() {
        AuthenticatedUser researcher = new AuthenticatedUser(UUID.randomUUID(), "researcher@test.local",
                Role.RESEARCHER, true);
        assertThrows(ForbiddenOperationException.class, () -> service.submit(researcher, caseId, requestId));
        verifyNoInteractions(cases, requests);
    }

    @Test
    void submissionRejectsAnIncompleteProposedUploadWithoutChangingState() {
        prepareEditableRequest(CaseStatus.INPUT_CHANGE_DRAFT);
        InputChangeRequestFileEntity membership = mock(InputChangeRequestFileEntity.class);
        StoredFileEntity incomplete = mock(StoredFileEntity.class);
        when(membership.getFile()).thenReturn(incomplete);
        when(membership.getRequest()).thenReturn(request);
        when(incomplete.getFileKind()).thenReturn(FileKind.INPUT);
        when(incomplete.getMedicalCase()).thenReturn(medicalCase);
        when(incomplete.getConfirmedAt()).thenReturn(null);
        when(requestFiles.findAllByRequest_IdAndFileSetAndRemovedAtIsNullOrderByAddedAtAsc(
                requestId, InputChangeFileSet.PROPOSED)).thenReturn(List.of(membership));

        assertThrows(BusinessRuleViolationException.class, () -> service.submit(owner, caseId, requestId));

        verify(request, never()).submit(any());
        verify(medicalCase, never()).setStatus(CaseStatus.INPUT_CHANGE_REVIEW);
    }

    @Test
    void submissionLocksAndTransitionsTheCaseAndRequest() {
        prepareEditableRequest(CaseStatus.INPUT_CHANGE_DRAFT);
        InputChangeRequestFileEntity membership = mock(InputChangeRequestFileEntity.class);
        StoredFileEntity complete = mock(StoredFileEntity.class);
        when(membership.getFile()).thenReturn(complete);
        when(membership.getRequest()).thenReturn(request);
        when(complete.getFileKind()).thenReturn(FileKind.INPUT);
        when(complete.getMedicalCase()).thenReturn(medicalCase);
        when(complete.getConfirmedAt()).thenReturn(Instant.parse("2026-10-15T11:59:00Z"));
        when(complete.getObjectKey()).thenReturn("input-change/proposed.pdf");
        when(requestFiles.findAllByRequest_IdAndFileSetAndRemovedAtIsNullOrderByAddedAtAsc(
                requestId, InputChangeFileSet.PROPOSED)).thenReturn(List.of(membership));
        when(storage.objectExists("input-change/proposed.pdf")).thenReturn(true);

        service.submit(owner, caseId, requestId);

        verify(request).submit(clock.instant());
        verify(requests).saveAndFlush(request);
        verify(medicalCase).setStatus(CaseStatus.INPUT_CHANGE_REVIEW);
        verify(cases).saveAndFlush(medicalCase);
    }

    @Test
    void secondSubmissionIsRejectedAfterTheFirstSubmission() {
        prepareEditableRequest(CaseStatus.INPUT_CHANGE_REVIEW);
        when(request.getStatus()).thenReturn(InputChangeRequestStatus.PENDING_REVIEW);

        assertThrows(BusinessRuleViolationException.class, () -> service.submit(owner, caseId, requestId));

        verify(request, never()).submit(any());
        verify(requests, never()).saveAndFlush(any());
    }

    @Test
    void cancellationQueuesOnlyStagedProposalFilesForCleanup() {
        prepareEditableRequest(CaseStatus.INPUT_CHANGE_DRAFT);
        AppUserEntity ownerEntity = mock(AppUserEntity.class);
        when(users.findById(ownerId)).thenReturn(Optional.of(ownerEntity));
        StoredFileEntity staged = mock(StoredFileEntity.class);
        when(staged.isInputActive()).thenReturn(false);
        when(staged.getId()).thenReturn(UUID.randomUUID());
        InputChangeRequestFileEntity stagedMembership = mock(InputChangeRequestFileEntity.class);
        when(stagedMembership.getFileSet()).thenReturn(InputChangeFileSet.PROPOSED);
        when(stagedMembership.getFile()).thenReturn(staged);
        StoredFileEntity current = mock(StoredFileEntity.class);
        when(current.isInputActive()).thenReturn(true);
        InputChangeRequestFileEntity currentMembership = mock(InputChangeRequestFileEntity.class);
        when(currentMembership.getFileSet()).thenReturn(InputChangeFileSet.PROPOSED);
        when(currentMembership.getFile()).thenReturn(current);
        when(request.getFiles()).thenReturn(List.of(stagedMembership, currentMembership));
        when(request.getResumeCaseStatus()).thenReturn(CaseStatus.ENABLED_FOR_PROCESSING);
        when(cleanup.existsById(staged.getId())).thenReturn(false);

        service.cancel(owner, caseId, requestId);

        verify(request).cancel(ownerEntity, clock.instant());
        verify(cleanup).save(any(InputChangeFileCleanupEntity.class));
        verify(medicalCase).setStatus(CaseStatus.ENABLED_FOR_PROCESSING);
        verify(current, never()).stageInput();
    }

    private void prepareEditableRequest(CaseStatus caseStatus) {
        when(cases.findByIdForUpdate(caseId)).thenReturn(Optional.of(medicalCase));
        when(medicalCase.getStatus()).thenReturn(caseStatus);
        when(requests.findByIdAndRequestedBy_Id(requestId, ownerId)).thenReturn(Optional.of(request));
    }
}
