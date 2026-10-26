package com.healthdocs.backend.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

import com.healthdocs.backend.adapter.out.persistence.*;
import com.healthdocs.backend.adapter.out.persistence.entity.*;
import com.healthdocs.backend.application.port.out.AuthProvider.AuthenticatedUser;
import com.healthdocs.backend.domain.model.*;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AcademicAssignmentsUseCaseTest {
    private final MedicalCaseRepository cases = mock(MedicalCaseRepository.class);
    private final CaseAssignmentRepository assignments = mock(CaseAssignmentRepository.class);
    private final AppUserRepository users = mock(AppUserRepository.class);
    private final StoredFileRepository files = mock(StoredFileRepository.class);
    private final AcademicAssignmentsUseCase useCase = new AcademicAssignmentsUseCase(cases, assignments, users, files);
    private final AuthenticatedUser coordinator = new AuthenticatedUser(UUID.randomUUID(), "coord@example.test", Role.COORDINATOR, true);

    @Test
    void firstResearcherAssignmentMovesCaseToProcessing() {
        UUID caseId = UUID.randomUUID(), researcherId = UUID.randomUUID();
        MedicalCaseEntity medicalCase = new MedicalCaseEntity(
                caseId, mock(AppUserEntity.class), CaseStatus.FILES_RECEIVED, "CASE-1", "TAC");
        AppUserEntity researcher = mock(AppUserEntity.class);
        when(cases.findByIdForUpdate(caseId)).thenReturn(Optional.of(medicalCase));
        when(cases.findById(caseId)).thenReturn(Optional.of(medicalCase));
        when(files.countByMedicalCaseIdAndFileKindAndInputActiveTrue(caseId, FileKind.INPUT)).thenReturn(1L);
        when(users.findById(researcherId)).thenReturn(Optional.of(researcher));
        when(researcher.getRole()).thenReturn(Role.RESEARCHER);
        when(researcher.isEnabled()).thenReturn(true);
        when(researcher.getAuthorizationStatus()).thenReturn(ResearcherAuthorizationStatus.AUTHORIZED);
        when(assignments.findAllByCaseId(caseId)).thenReturn(java.util.List.of());

        useCase.replaceCaseResearchers(coordinator, caseId, java.util.List.of(researcherId));

        assertEquals(CaseStatus.ENABLED_FOR_PROCESSING, medicalCase.getStatus());
        verify(assignments, times(2)).save(any(CaseAssignmentEntity.class));
        verify(assignments).save(argThat(assignment -> assignment.getCaseId().equals(caseId)
                && assignment.getUserId().equals(coordinator.id())));
        verify(cases).save(medicalCase);
    }

    @Test
    void removingLastResearcherReturnsProcessingCaseToFilesReceived() {
        UUID caseId = UUID.randomUUID(), researcherId = UUID.randomUUID();
        MedicalCaseEntity medicalCase = new MedicalCaseEntity(
                caseId, mock(AppUserEntity.class), CaseStatus.ENABLED_FOR_PROCESSING, "CASE-1", "TAC");
        AppUserEntity researcher = mock(AppUserEntity.class);
        when(cases.findByIdForUpdate(caseId)).thenReturn(Optional.of(medicalCase));
        when(cases.findById(caseId)).thenReturn(Optional.of(medicalCase));
        when(files.countByMedicalCaseIdAndFileKindAndInputActiveTrue(caseId, FileKind.INPUT)).thenReturn(1L);
        when(users.findById(researcherId)).thenReturn(Optional.of(researcher));
        when(researcher.getRole()).thenReturn(Role.RESEARCHER);
        when(researcher.getAuthorizationStatus()).thenReturn(ResearcherAuthorizationStatus.AUTHORIZED);
        when(assignments.findAllByCaseId(caseId)).thenReturn(java.util.List.of(new CaseAssignmentEntity(caseId, researcherId)));

        useCase.replaceCaseResearchers(coordinator, caseId, java.util.List.of());

        assertEquals(CaseStatus.FILES_RECEIVED, medicalCase.getStatus());
        verify(assignments).deleteByCaseIdAndUserId(caseId, researcherId);
        verify(cases).save(medicalCase);
    }

    @Test
    void restrictedResearcherCannotBeAddedToAnExistingCase() {
        UUID caseId = UUID.randomUUID(), researcherId = UUID.randomUUID();
        MedicalCaseEntity medicalCase = new MedicalCaseEntity(caseId, mock(AppUserEntity.class), CaseStatus.ENABLED_FOR_PROCESSING, "CASE-1", "TAC");
        AppUserEntity researcher = mock(AppUserEntity.class);
        when(cases.findByIdForUpdate(caseId)).thenReturn(Optional.of(medicalCase));
        when(files.countByMedicalCaseIdAndFileKind(caseId, FileKind.INPUT)).thenReturn(1L);
        when(users.findById(researcherId)).thenReturn(Optional.of(researcher));
        when(researcher.getRole()).thenReturn(Role.RESEARCHER);
        when(researcher.isEnabled()).thenReturn(true);
        when(researcher.getAuthorizationStatus()).thenReturn(ResearcherAuthorizationStatus.RESTRICTED);
        when(assignments.findAllByCaseId(caseId)).thenReturn(java.util.List.of());

        assertThrows(BusinessRuleViolationException.class,
                () -> useCase.replaceCaseResearchers(coordinator, caseId, java.util.List.of(researcherId)));
        verify(assignments, never()).save(any());
    }

    @Test
    void bulkCaseAssignmentIsBlockedWhileAnInputChangeRequestIsOpen() {
        UUID caseId = UUID.randomUUID();
        for (CaseStatus status : java.util.List.of(CaseStatus.INPUT_CHANGE_DRAFT, CaseStatus.INPUT_CHANGE_REVIEW)) {
            MedicalCaseEntity medicalCase = new MedicalCaseEntity(
                    caseId, mock(AppUserEntity.class), status, "CASE-1", "TAC");
            when(cases.findByIdForUpdate(caseId)).thenReturn(Optional.of(medicalCase));

            assertThrows(BusinessRuleViolationException.class,
                    () -> useCase.replaceCaseResearchers(coordinator, caseId, java.util.List.of(UUID.randomUUID())));
        }
        verify(assignments, never()).save(any());
    }

    @Test
    void bulkResearcherAssignmentIsBlockedWhileAnInputChangeRequestIsOpen() {
        UUID caseId = UUID.randomUUID(), researcherId = UUID.randomUUID();
        AppUserEntity researcher = mock(AppUserEntity.class);
        when(users.findById(researcherId)).thenReturn(Optional.of(researcher));
        when(researcher.getRole()).thenReturn(Role.RESEARCHER);
        when(researcher.isEnabled()).thenReturn(true);
        when(researcher.getAuthorizationStatus()).thenReturn(ResearcherAuthorizationStatus.AUTHORIZED);
        when(assignments.findAllByUserId(researcherId)).thenReturn(java.util.List.of());
        for (CaseStatus status : java.util.List.of(CaseStatus.INPUT_CHANGE_DRAFT, CaseStatus.INPUT_CHANGE_REVIEW)) {
            when(cases.findByIdForUpdate(caseId)).thenReturn(Optional.of(new MedicalCaseEntity(
                    caseId, mock(AppUserEntity.class), status, "CASE-1", "TAC")));

            assertThrows(BusinessRuleViolationException.class,
                    () -> useCase.replaceResearcherCases(coordinator, researcherId, java.util.List.of(caseId)));
        }
        verify(assignments, never()).save(any());
    }
}
