package com.healthdocs.backend.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.healthdocs.backend.adapter.out.persistence.AppUserRepository;
import com.healthdocs.backend.adapter.out.persistence.CaseAssignmentRepository;
import com.healthdocs.backend.adapter.out.persistence.CaseResultRepository;
import com.healthdocs.backend.adapter.out.persistence.MedicalCaseRepository;
import com.healthdocs.backend.adapter.out.persistence.StoredFileRepository;
import com.healthdocs.backend.adapter.out.persistence.entity.AppUserEntity;
import com.healthdocs.backend.adapter.out.persistence.entity.CaseAssignmentEntity;
import com.healthdocs.backend.adapter.out.persistence.entity.CaseResultEntity;
import com.healthdocs.backend.adapter.out.persistence.entity.MedicalCaseEntity;
import com.healthdocs.backend.adapter.out.persistence.entity.StoredFileEntity;
import com.healthdocs.backend.application.port.out.AuthProvider.AuthenticatedUser;
import com.healthdocs.backend.application.port.out.StoragePort;
import com.healthdocs.backend.domain.model.CaseStatus;
import com.healthdocs.backend.domain.model.FileKind;
import com.healthdocs.backend.domain.model.Role;
import com.healthdocs.backend.domain.model.ResearcherAuthorizationStatus;
import com.healthdocs.backend.domain.model.ResultPublicationType;
import com.healthdocs.backend.domain.model.ResultStatus;
import com.healthdocs.backend.infrastructure.config.InternalFlowProperties;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.lang.reflect.Field;
import org.junit.jupiter.api.Test;

class AssignmentFlowUseCasesTest {
    private final MedicalCaseRepository cases = org.mockito.Mockito.mock(MedicalCaseRepository.class);
    private final StoredFileRepository files = org.mockito.Mockito.mock(StoredFileRepository.class);
    private final CaseAssignmentRepository assignments = org.mockito.Mockito.mock(CaseAssignmentRepository.class);
    private final AppUserRepository users = org.mockito.Mockito.mock(AppUserRepository.class);
    private final CaseResultRepository results = org.mockito.Mockito.mock(CaseResultRepository.class);
    private final Clock clock = Clock.fixed(Instant.parse("2026-09-17T00:00:00Z"), ZoneOffset.UTC);

    @Test
    void enabledProfessorSeesOnlyPendingCasesReturnedByPendingQuery() {
        UUID caseId = UUID.randomUUID();
        MedicalCaseEntity pending = caseEntity(caseId, CaseStatus.FILES_RECEIVED);
        StoredFileEntity input = file(pending, FileKind.INPUT);
        when(cases.findPendingCases(CaseStatus.FILES_RECEIVED)).thenReturn(List.of(pending));
        when(files.findByMedicalCaseId(caseId)).thenReturn(List.of(input));

        var result = new ListPendingCasesUseCase(cases, files).execute(professor());

        assertEquals(List.of(caseId), result.stream().map(ListPendingCasesUseCase.PendingCaseView::id).toList());
        verify(cases).findPendingCases(CaseStatus.FILES_RECEIVED);
    }

    @Test
    void nonProfessorCannotListPendingCases() {
        assertThrows(ForbiddenOperationException.class,
                () -> new ListPendingCasesUseCase(cases, files).execute(
                        new AuthenticatedUser(UUID.randomUUID(), "student@test", Role.RESEARCHER, true)));
        verify(cases, never()).findPendingCases(any());
    }

    @Test
    void assignmentPersistsAndEnablesCase() {
        UUID caseId = UUID.randomUUID();
        UUID studentId = UUID.randomUUID();
        MedicalCaseEntity medicalCase = caseEntity(caseId, CaseStatus.FILES_RECEIVED);
        AppUserEntity student = org.mockito.Mockito.mock(AppUserEntity.class);
        when(cases.findByIdForUpdate(caseId)).thenReturn(java.util.Optional.of(medicalCase));
        when(files.countByMedicalCaseIdAndFileKindAndInputActiveTrue(caseId, FileKind.INPUT)).thenReturn(1L);
        when(users.findById(studentId)).thenReturn(java.util.Optional.of(student));
        when(student.isEnabled()).thenReturn(true);
        when(student.getRole()).thenReturn(Role.RESEARCHER);
        when(student.getAuthorizationStatus()).thenReturn(ResearcherAuthorizationStatus.AUTHORIZED);

        new AssignCaseUseCase(cases, files, assignments, users, clock).execute(professor(), caseId, studentId);

        assertEquals(CaseStatus.ENABLED_FOR_PROCESSING, medicalCase.getStatus());
        verify(assignments, org.mockito.Mockito.times(2)).save(any(CaseAssignmentEntity.class));
        verify(cases).save(medicalCase);
    }

    @Test
    void repeatedAssignmentDoesNotCreateAnotherRecord() {
        UUID caseId = UUID.randomUUID();
        UUID studentId = UUID.randomUUID();
        MedicalCaseEntity medicalCase = caseEntity(caseId, CaseStatus.FILES_RECEIVED);
        AppUserEntity student = org.mockito.Mockito.mock(AppUserEntity.class);
        when(cases.findByIdForUpdate(caseId)).thenReturn(java.util.Optional.of(medicalCase));
        when(files.countByMedicalCaseIdAndFileKindAndInputActiveTrue(caseId, FileKind.INPUT)).thenReturn(1L);
        when(users.findById(studentId)).thenReturn(java.util.Optional.of(student));
        when(student.isEnabled()).thenReturn(true);
        when(student.getRole()).thenReturn(Role.RESEARCHER);
        when(student.getAuthorizationStatus()).thenReturn(ResearcherAuthorizationStatus.AUTHORIZED);

        AuthenticatedUser coordinator = professor();
        when(assignments.existsByCaseIdAndUserId(caseId, coordinator.id())).thenReturn(true);
        when(assignments.existsByCaseIdAndUserId(caseId, studentId)).thenReturn(false, true);
        var useCase = new AssignCaseUseCase(cases, files, assignments, users, clock);
        useCase.execute(coordinator, caseId, studentId);
        useCase.execute(coordinator, caseId, studentId);

        verify(assignments).save(any(CaseAssignmentEntity.class));
    }

    @Test
    void restrictedResearcherCannotReceiveANewAssignment() {
        UUID caseId = UUID.randomUUID();
        UUID researcherId = UUID.randomUUID();
        MedicalCaseEntity medicalCase = caseEntity(caseId, CaseStatus.FILES_RECEIVED);
        AppUserEntity researcher = org.mockito.Mockito.mock(AppUserEntity.class);
        when(cases.findByIdForUpdate(caseId)).thenReturn(java.util.Optional.of(medicalCase));
        when(files.countByMedicalCaseIdAndFileKindAndInputActiveTrue(caseId, FileKind.INPUT)).thenReturn(1L);
        when(users.findById(researcherId)).thenReturn(java.util.Optional.of(researcher));
        when(researcher.isEnabled()).thenReturn(true);
        when(researcher.getRole()).thenReturn(Role.RESEARCHER);
        when(researcher.getAuthorizationStatus()).thenReturn(ResearcherAuthorizationStatus.RESTRICTED);
        when(assignments.existsByCaseIdAndUserId(caseId, researcherId)).thenReturn(false);

        assertThrows(BusinessRuleViolationException.class,
                () -> new AssignCaseUseCase(cases, files, assignments, users, clock)
                        .execute(professor(), caseId, researcherId));
        verify(assignments, never()).save(any());
    }

    @Test
    void assignmentIsBlockedWhileAnInputChangeRequestIsOpen() {
        UUID caseId = UUID.randomUUID();
        UUID researcherId = UUID.randomUUID();
        for (CaseStatus status : List.of(CaseStatus.INPUT_CHANGE_DRAFT, CaseStatus.INPUT_CHANGE_REVIEW)) {
            MedicalCaseEntity medicalCase = caseEntity(caseId, status);
            when(cases.findByIdForUpdate(caseId)).thenReturn(java.util.Optional.of(medicalCase));

            assertThrows(BusinessRuleViolationException.class,
                    () -> new AssignCaseUseCase(cases, files, assignments, users, clock)
                            .execute(professor(), caseId, researcherId));
        }
        verify(assignments, never()).save(any());
    }

    @Test
    void assignedUserSeesCaseAndUnassignedUserDoesNot() {
        UUID caseId = UUID.randomUUID();
        UUID assignedId = UUID.randomUUID();
        UUID otherId = UUID.randomUUID();
        MedicalCaseEntity medicalCase = caseEntity(caseId, CaseStatus.ENABLED_FOR_PROCESSING);
        List<CaseStatus> activeStatuses = List.of(CaseStatus.ENABLED_FOR_PROCESSING, CaseStatus.INPUT_CHANGE_DRAFT,
                CaseStatus.INPUT_CHANGE_REVIEW, CaseStatus.FILES_RECEIVED,
                CaseStatus.RESULT_UPLOADED, CaseStatus.RESULT_AVAILABLE);
        when(cases.findAssignedInternalCases(assignedId, activeStatuses)).thenReturn(List.of(medicalCase));
        when(cases.findAssignedInternalCases(otherId, activeStatuses)).thenReturn(List.of());
        when(files.findByMedicalCaseId(caseId)).thenReturn(List.of(file(medicalCase, FileKind.INPUT)));
        var flow = new InternalFlowService(cases, files, assignments, results,
                org.mockito.Mockito.mock(StoragePort.class), org.mockito.Mockito.mock(InternalFlowProperties.class), clock);

        assertEquals(1, flow.list(new AuthenticatedUser(assignedId, "student@test", Role.RESEARCHER, true)).size());
        assertEquals(0, flow.list(new AuthenticatedUser(otherId, "other@test", Role.RESEARCHER, true)).size());
        verify(cases).findAssignedInternalCases(assignedId, activeStatuses);
        verify(cases).findAssignedInternalCases(otherId, activeStatuses);
    }

    @Test
    void assignedInternalUserCanDownloadInputAfterResultIsUploadedOrAvailable() {
        UUID caseId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID fileId = UUID.randomUUID();
        var storage = org.mockito.Mockito.mock(StoragePort.class);
        var properties = org.mockito.Mockito.mock(InternalFlowProperties.class);
        var url = new StoragePort.UploadUrlResult("http://localhost/download", Instant.MAX);

        when(assignments.existsByCaseIdAndUserId(caseId, userId)).thenReturn(true);
        when(storage.generateDownloadUrl("input/file.dcm", "application/dicom", 10)).thenReturn(url);
        when(properties.downloadTtlMinutes()).thenReturn(10);

        var flow = new InternalFlowService(cases, files, assignments, results, storage, properties, clock);
        for (Role role : List.of(Role.RESEARCHER, Role.COORDINATOR)) {
            var user = new AuthenticatedUser(userId, role.name().toLowerCase() + "@test", role, true);
            for (CaseStatus status : List.of(CaseStatus.RESULT_UPLOADED, CaseStatus.RESULT_AVAILABLE)) {
                MedicalCaseEntity medicalCase = caseEntity(caseId, status);
                StoredFileEntity input = new StoredFileEntity(
                        fileId, medicalCase, "input/file.dcm", FileKind.INPUT, "file.dcm", "application/dicom", 10L);
                when(cases.findById(caseId)).thenReturn(java.util.Optional.of(medicalCase));
                when(files.findById(fileId)).thenReturn(java.util.Optional.of(input));

                assertEquals("http://localhost/download", flow.download(user, caseId, fileId).downloadUrl());
            }
        }
    }

    @Test
    void professorPublishesResultAndEmitsOneEvent() throws Exception {
        UUID caseId = UUID.randomUUID();
        UUID resultId = UUID.randomUUID();
        UUID patientId = UUID.randomUUID();
        UUID professorId = UUID.randomUUID();
        AppUserEntity patient = org.mockito.Mockito.mock(AppUserEntity.class);
        when(patient.getId()).thenReturn(patientId);
        when(patient.getEmail()).thenReturn("patient@test.example");
        MedicalCaseEntity medicalCase = new MedicalCaseEntity(caseId, patient, CaseStatus.RESULT_UPLOADED, "CASE-1", "TAC");
        CaseResultEntity result = new CaseResultEntity(medicalCase,
                file(medicalCase, FileKind.RESULT), ResultStatus.UPLOADED);
        setId(result, resultId);
        when(results.findById(resultId)).thenReturn(java.util.Optional.of(result));
        when(assignments.existsByCaseIdAndUserId(caseId, professorId)).thenReturn(true);
        List<String> outboxKeys = new java.util.ArrayList<>();
        var flow = new InternalFlowService(cases, files, assignments, results,
                org.mockito.Mockito.mock(StoragePort.class), org.mockito.Mockito.mock(InternalFlowProperties.class),
                clock);
        flow.setNotificationOutboxPort((type, key, caseRef, recipient, publishedBy, relatedType, relatedId, occurredAt) -> {
            assertEquals("RESULT_PUBLISHED", type);
            assertEquals(caseId, caseRef);
            assertEquals(patientId, recipient);
            assertEquals(professorId, publishedBy);
            assertEquals("RESULT", relatedType);
            assertEquals(resultId, relatedId);
            outboxKeys.add(key);
        });

        flow.makeResultAvailable(new AuthenticatedUser(professorId, "professor@test", Role.COORDINATOR, true), resultId);

        assertEquals(ResultStatus.AVAILABLE, result.getStatus());
        assertEquals(1, result.getVersionNumber());
        assertEquals(ResultPublicationType.INITIAL, result.getPublicationType());
        org.junit.jupiter.api.Assertions.assertTrue(result.isCurrent());
        assertEquals(CaseStatus.RESULT_AVAILABLE, medicalCase.getStatus());
        assertEquals(professorId, result.getAvailableBy());
        assertEquals(List.of("RESULT_PUBLISHED:" + resultId + ":1"), outboxKeys);

        assertThrows(BusinessRuleViolationException.class, () -> flow.makeResultAvailable(
                new AuthenticatedUser(professorId, "professor@test", Role.COORDINATOR, true), resultId));
        assertEquals(1, outboxKeys.size());
    }

    @Test
    void unauthorizedProfessorCannotModifyResultOrCase() throws Exception {
        UUID caseId = UUID.randomUUID();
        UUID resultId = UUID.randomUUID();
        MedicalCaseEntity medicalCase = caseEntity(caseId, CaseStatus.RESULT_UPLOADED);
        CaseResultEntity result = new CaseResultEntity(medicalCase, file(medicalCase, FileKind.RESULT), ResultStatus.UPLOADED);
        setId(result, resultId);
        when(results.findById(resultId)).thenReturn(java.util.Optional.of(result));
        when(assignments.existsByCaseIdAndUserId(caseId, UUID.randomUUID())).thenReturn(false);
        var flow = new InternalFlowService(cases, files, assignments, results,
                org.mockito.Mockito.mock(StoragePort.class), org.mockito.Mockito.mock(InternalFlowProperties.class), clock);

        assertThrows(ForbiddenOperationException.class, () -> flow.makeResultAvailable(
                new AuthenticatedUser(UUID.randomUUID(), "student@test", Role.RESEARCHER, true), resultId));
        assertEquals(ResultStatus.UPLOADED, result.getStatus());
        assertEquals(CaseStatus.RESULT_UPLOADED, medicalCase.getStatus());
        verify(results, never()).save(any());
        verify(cases, never()).save(any());
    }

    @Test
    void patientGetsOnlyAvailableResultAndConfiguredTemporaryUrl() throws Exception {
        UUID patientId = UUID.randomUUID();
        UUID caseId = UUID.randomUUID();
        MedicalCaseEntity medicalCase = new MedicalCaseEntity(caseId, patientEntity(patientId), CaseStatus.RESULT_AVAILABLE, "CASE-1", "TAC");
        StoredFileEntity resultFile = new StoredFileEntity(UUID.randomUUID(), medicalCase, "result/CASE-1/result.pdf", FileKind.RESULT, "result.pdf", "application/pdf", 10L);
        CaseResultEntity result = new CaseResultEntity(medicalCase, resultFile, ResultStatus.AVAILABLE);
        result.publishAsVersion(1, ResultPublicationType.INITIAL, clock.instant(), UUID.randomUUID());
        setId(result, UUID.randomUUID());
        StoragePort storage = org.mockito.Mockito.mock(StoragePort.class);
        when(cases.findById(caseId)).thenReturn(java.util.Optional.of(medicalCase));
        when(results.findByMedicalCaseIdAndCurrentTrueAndDeletedAtIsNull(caseId)).thenReturn(java.util.Optional.of(result));
        when(storage.objectExists(resultFile.getObjectKey())).thenReturn(true);
        when(storage.generateDownloadUrl(resultFile.getObjectKey(), resultFile.getContentType(), 7))
                .thenReturn(new StoragePort.UploadUrlResult("https://temporary.test/token", clock.instant().plusSeconds(420)));
        InternalFlowProperties properties = org.mockito.Mockito.mock(InternalFlowProperties.class);
        when(properties.downloadTtlMinutes()).thenReturn(7);
        InternalFlowService service = new InternalFlowService(cases, files, assignments, results, storage, properties, clock);

        var response = service.patientResult(new AuthenticatedUser(patientId, "patient@test", Role.PATIENT, true), caseId);

        assertEquals("https://temporary.test/token", response.download().downloadUrl());
        assertEquals(clock.instant().plusSeconds(420), response.download().expiresAt());
        verify(storage).generateDownloadUrl(resultFile.getObjectKey(), resultFile.getContentType(), 7);
    }

    @Test
    void patientCannotGetResultBeforeCaseIsPublished() {
        UUID patientId = UUID.randomUUID();
        UUID caseId = UUID.randomUUID();
        MedicalCaseEntity medicalCase = new MedicalCaseEntity(caseId, patientEntity(patientId), CaseStatus.RESULT_UPLOADED, "CASE-1", "TAC");
        when(cases.findById(caseId)).thenReturn(java.util.Optional.of(medicalCase));
        StoragePort storage = org.mockito.Mockito.mock(StoragePort.class);
        InternalFlowProperties properties = org.mockito.Mockito.mock(InternalFlowProperties.class);
        InternalFlowService service = new InternalFlowService(cases, files, assignments, results, storage, properties, clock);

        assertThrows(BusinessRuleViolationException.class, () -> service.patientResult(
                new AuthenticatedUser(patientId, "patient@test", Role.PATIENT, true), caseId));
        verify(storage, never()).generateDownloadUrl(any(), any(), any(Integer.class));
    }

    @Test
    void patientCannotGetResultWhenStoredObjectIsMissing() {
        UUID patientId = UUID.randomUUID();
        UUID caseId = UUID.randomUUID();
        MedicalCaseEntity medicalCase = new MedicalCaseEntity(caseId, patientEntity(patientId), CaseStatus.RESULT_AVAILABLE, "CASE-1", "TAC");
        StoredFileEntity resultFile = new StoredFileEntity(UUID.randomUUID(), medicalCase, "result/missing.pdf", FileKind.RESULT, "missing.pdf", "application/pdf", 10L);
        CaseResultEntity result = new CaseResultEntity(medicalCase, resultFile, ResultStatus.AVAILABLE);
        result.publishAsVersion(1, ResultPublicationType.INITIAL, clock.instant(), UUID.randomUUID());
        when(cases.findById(caseId)).thenReturn(java.util.Optional.of(medicalCase));
        when(results.findByMedicalCaseIdAndCurrentTrueAndDeletedAtIsNull(caseId)).thenReturn(java.util.Optional.of(result));
        StoragePort storage = org.mockito.Mockito.mock(StoragePort.class);
        when(storage.objectExists(resultFile.getObjectKey())).thenReturn(false);
        InternalFlowProperties properties = org.mockito.Mockito.mock(InternalFlowProperties.class);
        InternalFlowService service = new InternalFlowService(cases, files, assignments, results, storage, properties, clock);

        assertThrows(ResourceNotFoundException.class, () -> service.patientResult(
                new AuthenticatedUser(patientId, "patient@test", Role.PATIENT, true), caseId));
    }

    @Test
    void confirmingInitialUploadMakesPendingVersionCurrentAndAwaitsReview() throws Exception {
        UUID caseId = UUID.randomUUID();
        UUID fileId = UUID.randomUUID();
        UUID researcherId = UUID.randomUUID();
        MedicalCaseEntity medicalCase = caseEntity(caseId, CaseStatus.ENABLED_FOR_PROCESSING);
        StoredFileEntity uploadedFile = new StoredFileEntity(fileId, medicalCase, "result/case/initial.pdf",
                FileKind.RESULT, "initial.pdf", "application/pdf", 20L, researcherId);
        when(files.findById(fileId)).thenReturn(java.util.Optional.of(uploadedFile));
        when(cases.findByIdForUpdate(caseId)).thenReturn(java.util.Optional.of(medicalCase));
        when(assignments.existsByCaseIdAndUserId(caseId, researcherId)).thenReturn(true);
        when(results.existsByMedicalCaseIdAndStatusAndDeletedAtIsNull(caseId, ResultStatus.UPLOADED)).thenReturn(false);
        when(results.findByMedicalCaseIdAndCurrentTrueAndDeletedAtIsNull(caseId)).thenReturn(java.util.Optional.empty());
        when(results.save(any(CaseResultEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(results.findMaximumVersionNumber(caseId)).thenReturn(0);
        AppUserEntity authorizedResearcher = org.mockito.Mockito.mock(AppUserEntity.class);
        when(users.findById(researcherId)).thenReturn(java.util.Optional.of(authorizedResearcher));
        when(authorizedResearcher.isEnabled()).thenReturn(true);
        when(authorizedResearcher.getAuthorizationStatus()).thenReturn(ResearcherAuthorizationStatus.AUTHORIZED);
        StoragePort storage = org.mockito.Mockito.mock(StoragePort.class);
        when(storage.objectExists(uploadedFile.getObjectKey())).thenReturn(true);
        InternalFlowService flow = new InternalFlowService(cases, files, assignments, results,
                storage, org.mockito.Mockito.mock(InternalFlowProperties.class), clock, users);

        var confirmation = flow.confirm(
                new AuthenticatedUser(researcherId, "researcher@test", Role.RESEARCHER, true), fileId);

        assertEquals(CaseStatus.RESULT_UPLOADED, medicalCase.getStatus());
        assertEquals(ResultStatus.UPLOADED, confirmation.result().status());
        assertEquals(1, confirmation.result().version());
        assertEquals(ResultPublicationType.INITIAL, confirmation.result().publicationType());
        org.mockito.ArgumentCaptor<CaseResultEntity> savedResult = org.mockito.ArgumentCaptor.forClass(CaseResultEntity.class);
        verify(results).save(savedResult.capture());
        CaseResultEntity pending = savedResult.getValue();
        assertEquals(ResultStatus.UPLOADED, pending.getStatus());
        assertEquals(1, pending.getVersionNumber());
        assertEquals(ResultPublicationType.INITIAL, pending.getPublicationType());
        org.junit.jupiter.api.Assertions.assertTrue(pending.isCurrent());
    }

    @Test
    void resultUploadIntentIsRejectedOutsideEnabledProcessing() {
        UUID caseId = UUID.randomUUID();
        UUID researcherId = UUID.randomUUID();
        AppUserEntity authorizedResearcher = org.mockito.Mockito.mock(AppUserEntity.class);
        when(users.findById(researcherId)).thenReturn(java.util.Optional.of(authorizedResearcher));
        when(authorizedResearcher.isEnabled()).thenReturn(true);
        when(authorizedResearcher.getAuthorizationStatus()).thenReturn(ResearcherAuthorizationStatus.AUTHORIZED);
        InternalFlowService flow = new InternalFlowService(cases, files, assignments, results,
                org.mockito.Mockito.mock(StoragePort.class), org.mockito.Mockito.mock(InternalFlowProperties.class), clock, users);

        for (CaseStatus status : List.of(CaseStatus.FILES_RECEIVED, CaseStatus.INPUT_CHANGE_DRAFT,
                CaseStatus.INPUT_CHANGE_REVIEW, CaseStatus.RESULT_UPLOADED, CaseStatus.RESULT_AVAILABLE)) {
            when(cases.findById(caseId)).thenReturn(java.util.Optional.of(caseEntity(caseId, status)));
            assertThrows(ForbiddenOperationException.class, () -> flow.createResult(
                    new AuthenticatedUser(researcherId, "researcher@test", Role.RESEARCHER, true),
                    caseId, "result.pdf", "application/pdf", 10L));
        }
        verify(files, never()).save(any());
    }

    @Test
    void correctionRequestHidesResultFromPatientAndRetainsReason() throws Exception {
        UUID caseId = UUID.randomUUID();
        UUID resultId = UUID.randomUUID();
        UUID coordinatorId = UUID.randomUUID();
        MedicalCaseEntity medicalCase = caseEntity(caseId, CaseStatus.RESULT_AVAILABLE);
        CaseResultEntity result = new CaseResultEntity(medicalCase, file(medicalCase, FileKind.RESULT), ResultStatus.AVAILABLE);
        result.publishAsVersion(1, ResultPublicationType.INITIAL, clock.instant(), coordinatorId);
        setId(result, resultId);
        when(results.findById(resultId)).thenReturn(java.util.Optional.of(result));
        when(cases.findByIdForUpdate(caseId)).thenReturn(java.util.Optional.of(medicalCase));
        when(assignments.existsByCaseIdAndUserId(caseId, coordinatorId)).thenReturn(true);

        InternalFlowService flow = new InternalFlowService(cases, files, assignments, results,
                org.mockito.Mockito.mock(StoragePort.class), org.mockito.Mockito.mock(InternalFlowProperties.class), clock);
        flow.requestCorrection(new AuthenticatedUser(coordinatorId, "coordinator@test", Role.COORDINATOR, true),
                resultId, "Revisar conclusión");

        assertEquals(ResultStatus.CORRECTION_REQUIRED, result.getStatus());
        assertEquals("Revisar conclusión", result.getCorrectionRequest());
        assertEquals(CaseStatus.ENABLED_FOR_PROCESSING, medicalCase.getStatus());
        org.junit.jupiter.api.Assertions.assertTrue(result.isCurrent());
    }

    @Test
    void coordinatorCanReturnUploadedPendingResultForCorrection() throws Exception {
        UUID caseId = UUID.randomUUID();
        UUID resultId = UUID.randomUUID();
        UUID coordinatorId = UUID.randomUUID();
        MedicalCaseEntity medicalCase = caseEntity(caseId, CaseStatus.RESULT_UPLOADED);
        CaseResultEntity result = new CaseResultEntity(medicalCase, file(medicalCase, FileKind.RESULT), ResultStatus.UPLOADED);
        result.reserveVersion(1, ResultPublicationType.INITIAL);
        setId(result, resultId);
        when(results.findById(resultId)).thenReturn(java.util.Optional.of(result));
        when(cases.findByIdForUpdate(caseId)).thenReturn(java.util.Optional.of(medicalCase));
        when(assignments.existsByCaseIdAndUserId(caseId, coordinatorId)).thenReturn(true);

        InternalFlowService flow = new InternalFlowService(cases, files, assignments, results,
                org.mockito.Mockito.mock(StoragePort.class), org.mockito.Mockito.mock(InternalFlowProperties.class), clock);
        flow.requestCorrection(new AuthenticatedUser(coordinatorId, "coordinator@test", Role.COORDINATOR, true),
                resultId, "Falta incluir la conclusión");

        assertEquals(ResultStatus.CORRECTION_REQUIRED, result.getStatus());
        assertEquals("Falta incluir la conclusión", result.getCorrectionRequest());
        assertEquals(CaseStatus.ENABLED_FOR_PROCESSING, medicalCase.getStatus());
    }

    @Test
    void confirmingCorrectedUploadAttendsRequestAndLeavesNewVersionPending() throws Exception {
        UUID caseId = UUID.randomUUID();
        UUID fileId = UUID.randomUUID();
        UUID researcherId = UUID.randomUUID();
        MedicalCaseEntity medicalCase = caseEntity(caseId, CaseStatus.ENABLED_FOR_PROCESSING);
        StoredFileEntity uploadedFile = new StoredFileEntity(fileId, medicalCase, "result/case/new.pdf", FileKind.RESULT,
                "new.pdf", "application/pdf", 10L, researcherId);
        CaseResultEntity prior = new CaseResultEntity(medicalCase, file(medicalCase, FileKind.RESULT), ResultStatus.AVAILABLE);
        prior.publishAsVersion(1, ResultPublicationType.INITIAL, clock.instant(), UUID.randomUUID());
        prior.requestCorrection("Revisar conclusión");
        when(files.findById(fileId)).thenReturn(java.util.Optional.of(uploadedFile));
        when(cases.findByIdForUpdate(caseId)).thenReturn(java.util.Optional.of(medicalCase));
        when(assignments.existsByCaseIdAndUserId(caseId, researcherId)).thenReturn(true);
        when(results.existsByMedicalCaseIdAndStatusAndDeletedAtIsNull(caseId, ResultStatus.UPLOADED)).thenReturn(false);
        when(results.findByMedicalCaseIdAndCurrentTrueAndDeletedAtIsNull(caseId)).thenReturn(java.util.Optional.of(prior));
        when(results.save(any(CaseResultEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        AppUserEntity authorizedResearcher = org.mockito.Mockito.mock(AppUserEntity.class);
        when(users.findById(researcherId)).thenReturn(java.util.Optional.of(authorizedResearcher));
        when(authorizedResearcher.isEnabled()).thenReturn(true);
        when(authorizedResearcher.getAuthorizationStatus()).thenReturn(ResearcherAuthorizationStatus.AUTHORIZED);
        StoragePort storage = org.mockito.Mockito.mock(StoragePort.class);
        when(storage.objectExists(uploadedFile.getObjectKey())).thenReturn(true);

        InternalFlowService flow = new InternalFlowService(cases, files, assignments, results,
                storage, org.mockito.Mockito.mock(InternalFlowProperties.class), clock, users);
        flow.confirm(new AuthenticatedUser(researcherId, "researcher@test", Role.RESEARCHER, true), fileId);

        assertEquals(ResultStatus.AVAILABLE, prior.getStatus());
        org.junit.jupiter.api.Assertions.assertNull(prior.getCorrectionRequest());
        assertEquals("Revisar conclusión", prior.getCorrectionReason());
        org.junit.jupiter.api.Assertions.assertFalse(prior.isCurrent());
        org.mockito.ArgumentCaptor<CaseResultEntity> savedCorrectedResults = org.mockito.ArgumentCaptor.forClass(CaseResultEntity.class);
        verify(results, org.mockito.Mockito.times(2)).save(savedCorrectedResults.capture());
        org.junit.jupiter.api.Assertions.assertTrue(savedCorrectedResults.getAllValues().get(1).isCurrent());
        assertEquals(CaseStatus.RESULT_UPLOADED, medicalCase.getStatus());
    }

    @Test
    void publishingCorrectedUploadAssignsNextVersionAndReplacesCurrent() throws Exception {
        UUID caseId = UUID.randomUUID();
        UUID pendingId = UUID.randomUUID();
        UUID coordinatorId = UUID.randomUUID();
        MedicalCaseEntity medicalCase = caseEntity(caseId, CaseStatus.RESULT_UPLOADED);
        CaseResultEntity previous = new CaseResultEntity(medicalCase, file(medicalCase, FileKind.RESULT), ResultStatus.AVAILABLE);
        previous.publishAsVersion(1, ResultPublicationType.INITIAL, clock.instant(), UUID.randomUUID());
        previous.resolveCorrectionAsHistory();
        CaseResultEntity corrected = new CaseResultEntity(medicalCase, file(medicalCase, FileKind.RESULT), ResultStatus.UPLOADED);
        setId(corrected, pendingId);
        when(results.findById(pendingId)).thenReturn(java.util.Optional.of(corrected));
        when(cases.findByIdForUpdate(caseId)).thenReturn(java.util.Optional.of(medicalCase));
        when(assignments.existsByCaseIdAndUserId(caseId, coordinatorId)).thenReturn(true);
        when(results.findMaximumVersionNumber(caseId)).thenReturn(1);
        var flow = new InternalFlowService(cases, files, assignments, results,
                org.mockito.Mockito.mock(StoragePort.class), org.mockito.Mockito.mock(InternalFlowProperties.class), clock);

        flow.makeResultAvailable(new AuthenticatedUser(coordinatorId, "coordinator@test", Role.COORDINATOR, true), pendingId);

        assertEquals(2, corrected.getVersionNumber());
        assertEquals(ResultPublicationType.CORRECTED, corrected.getPublicationType());
        assertEquals(ResultStatus.AVAILABLE, corrected.getStatus());
        org.junit.jupiter.api.Assertions.assertTrue(corrected.isCurrent());
        org.junit.jupiter.api.Assertions.assertFalse(previous.isCurrent());
    }

    @Test
    void assignedAuthorizedResearcherCanDeletePendingUploadAndRetry() throws Exception {
        UUID caseId = UUID.randomUUID();
        UUID resultId = UUID.randomUUID();
        UUID researcherId = UUID.randomUUID();
        MedicalCaseEntity medicalCase = caseEntity(caseId, CaseStatus.RESULT_UPLOADED);
        StoredFileEntity resultFile = file(medicalCase, FileKind.RESULT);
        CaseResultEntity result = new CaseResultEntity(medicalCase, resultFile, ResultStatus.UPLOADED);
        result.reserveVersion(1, ResultPublicationType.INITIAL);
        setId(result, resultId);
        when(results.findById(resultId)).thenReturn(java.util.Optional.of(result));
        when(cases.findById(caseId)).thenReturn(java.util.Optional.of(medicalCase));
        when(cases.findByIdForUpdate(caseId)).thenReturn(java.util.Optional.of(medicalCase));
        when(assignments.existsByCaseIdAndUserId(caseId, researcherId)).thenReturn(true);
        AppUserEntity authorizedResearcher = org.mockito.Mockito.mock(AppUserEntity.class);
        when(users.findById(researcherId)).thenReturn(java.util.Optional.of(authorizedResearcher));
        when(authorizedResearcher.isEnabled()).thenReturn(true);
        when(authorizedResearcher.getAuthorizationStatus()).thenReturn(ResearcherAuthorizationStatus.AUTHORIZED);

        StoragePort storage = org.mockito.Mockito.mock(StoragePort.class);
        InternalFlowService flow = new InternalFlowService(cases, files, assignments, results,
                storage, org.mockito.Mockito.mock(InternalFlowProperties.class), clock, users);
        flow.deleteResult(new AuthenticatedUser(researcherId, "researcher@test", Role.RESEARCHER, true),
                caseId, resultId, "Carga incorrecta");

        assertEquals(ResultStatus.DELETED, result.getStatus());
        assertEquals(researcherId, result.getDeletedBy());
        assertEquals("Carga incorrecta", result.getDeletionReason());
        org.junit.jupiter.api.Assertions.assertFalse(result.isCurrent());
        assertEquals(CaseStatus.ENABLED_FOR_PROCESSING, medicalCase.getStatus());
        verify(results).save(result);
        verify(results, never()).delete(result);
        verify(files, never()).delete(resultFile);
        verify(storage, never()).deleteObject(resultFile.getObjectKey());
    }

    @Test
    void researcherCannotDeletePublishedResultAndCoordinatorCannotUseResearcherDelete() throws Exception {
        UUID caseId = UUID.randomUUID();
        UUID resultId = UUID.randomUUID();
        UUID researcherId = UUID.randomUUID();
        UUID coordinatorId = UUID.randomUUID();
        MedicalCaseEntity medicalCase = caseEntity(caseId, CaseStatus.RESULT_AVAILABLE);
        CaseResultEntity result = new CaseResultEntity(medicalCase, file(medicalCase, FileKind.RESULT), ResultStatus.AVAILABLE);
        result.publishAsVersion(1, ResultPublicationType.INITIAL, clock.instant(), coordinatorId);
        setId(result, resultId);
        when(results.findById(resultId)).thenReturn(java.util.Optional.of(result));
        when(cases.findById(caseId)).thenReturn(java.util.Optional.of(medicalCase));
        when(cases.findByIdForUpdate(caseId)).thenReturn(java.util.Optional.of(medicalCase));
        when(assignments.existsByCaseIdAndUserId(caseId, researcherId)).thenReturn(true);
        AppUserEntity authorizedResearcher = org.mockito.Mockito.mock(AppUserEntity.class);
        when(users.findById(researcherId)).thenReturn(java.util.Optional.of(authorizedResearcher));
        when(authorizedResearcher.isEnabled()).thenReturn(true);
        when(authorizedResearcher.getAuthorizationStatus()).thenReturn(ResearcherAuthorizationStatus.AUTHORIZED);
        InternalFlowService flow = new InternalFlowService(cases, files, assignments, results,
                org.mockito.Mockito.mock(StoragePort.class), org.mockito.Mockito.mock(InternalFlowProperties.class), clock, users);

        assertThrows(BusinessRuleViolationException.class, () -> flow.deleteResult(
                new AuthenticatedUser(researcherId, "researcher@test", Role.RESEARCHER, true),
                caseId, resultId, "Carga incorrecta"));
        assertThrows(ForbiddenOperationException.class, () -> flow.deleteResult(
                new AuthenticatedUser(coordinatorId, "coordinator@test", Role.COORDINATOR, true),
                caseId, resultId, "Carga incorrecta"));
        assertEquals(ResultStatus.AVAILABLE, result.getStatus());
        assertEquals(CaseStatus.RESULT_AVAILABLE, medicalCase.getStatus());
        verify(results, never()).save(result);
        verify(cases, never()).save(medicalCase);
    }

    private AppUserEntity patientEntity(UUID id) {
        AppUserEntity patient = org.mockito.Mockito.mock(AppUserEntity.class);
        when(patient.getId()).thenReturn(id);
        return patient;
    }

    private void setId(Object entity, UUID id) throws Exception {
        Field field = entity.getClass().getDeclaredField("id");
        field.setAccessible(true);
        field.set(entity, id);
    }

    private AuthenticatedUser professor() {
        return new AuthenticatedUser(UUID.randomUUID(), "professor@test", Role.COORDINATOR, true);
    }

    private MedicalCaseEntity caseEntity(UUID id, CaseStatus status) {
        return new MedicalCaseEntity(id, org.mockito.Mockito.mock(AppUserEntity.class), status, "CASE-" + id, "TAC");
    }

    private StoredFileEntity file(MedicalCaseEntity medicalCase, FileKind kind) {
        return new StoredFileEntity(UUID.randomUUID(), medicalCase, "input/file.dcm", kind, "file.dcm", "application/dicom", 10L);
    }
}
