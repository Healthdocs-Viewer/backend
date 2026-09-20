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
import com.healthdocs.backend.domain.model.ResultAvailableEvent;
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
                        new AuthenticatedUser(UUID.randomUUID(), "student@test", Role.ESTUDIANTE, true)));
        verify(cases, never()).findPendingCases(any());
    }

    @Test
    void assignmentPersistsAndEnablesCase() {
        UUID caseId = UUID.randomUUID();
        UUID studentId = UUID.randomUUID();
        MedicalCaseEntity medicalCase = caseEntity(caseId, CaseStatus.FILES_RECEIVED);
        AppUserEntity student = org.mockito.Mockito.mock(AppUserEntity.class);
        when(cases.findById(caseId)).thenReturn(java.util.Optional.of(medicalCase));
        when(files.countByMedicalCaseIdAndFileKind(caseId, FileKind.INPUT)).thenReturn(1L);
        when(users.findById(studentId)).thenReturn(java.util.Optional.of(student));
        when(student.isEnabled()).thenReturn(true);
        when(student.getRole()).thenReturn(Role.ESTUDIANTE);

        new AssignCaseUseCase(cases, files, assignments, users, clock).execute(professor(), caseId, studentId);

        assertEquals(CaseStatus.ENABLED_FOR_PROCESSING, medicalCase.getStatus());
        verify(assignments).save(any(CaseAssignmentEntity.class));
        verify(cases).save(medicalCase);
    }

    @Test
    void repeatedAssignmentDoesNotCreateAnotherRecord() {
        UUID caseId = UUID.randomUUID();
        UUID studentId = UUID.randomUUID();
        MedicalCaseEntity medicalCase = caseEntity(caseId, CaseStatus.FILES_RECEIVED);
        AppUserEntity student = org.mockito.Mockito.mock(AppUserEntity.class);
        when(cases.findById(caseId)).thenReturn(java.util.Optional.of(medicalCase));
        when(files.countByMedicalCaseIdAndFileKind(caseId, FileKind.INPUT)).thenReturn(1L);
        when(users.findById(studentId)).thenReturn(java.util.Optional.of(student));
        when(student.isEnabled()).thenReturn(true);
        when(student.getRole()).thenReturn(Role.ESTUDIANTE);

        var useCase = new AssignCaseUseCase(cases, files, assignments, users, clock);
        useCase.execute(professor(), caseId, studentId);
        assertThrows(BusinessRuleViolationException.class, () -> useCase.execute(professor(), caseId, studentId));

        verify(assignments).save(any(CaseAssignmentEntity.class));
    }

    @Test
    void assignedUserSeesCaseAndUnassignedUserDoesNot() {
        UUID caseId = UUID.randomUUID();
        UUID assignedId = UUID.randomUUID();
        UUID otherId = UUID.randomUUID();
        MedicalCaseEntity medicalCase = caseEntity(caseId, CaseStatus.ENABLED_FOR_PROCESSING);
        when(cases.findEnabledForUser(assignedId, CaseStatus.ENABLED_FOR_PROCESSING)).thenReturn(List.of(medicalCase));
        when(cases.findEnabledForUser(otherId, CaseStatus.ENABLED_FOR_PROCESSING)).thenReturn(List.of());
        when(files.findByMedicalCaseId(caseId)).thenReturn(List.of(file(medicalCase, FileKind.INPUT)));
        var flow = new InternalFlowService(cases, files, assignments, results,
                org.mockito.Mockito.mock(StoragePort.class), org.mockito.Mockito.mock(InternalFlowProperties.class), clock);

        assertEquals(1, flow.list(new AuthenticatedUser(assignedId, "student@test", Role.ESTUDIANTE, true)).size());
        assertEquals(0, flow.list(new AuthenticatedUser(otherId, "other@test", Role.ESTUDIANTE, true)).size());
        verify(cases).findEnabledForUser(assignedId, CaseStatus.ENABLED_FOR_PROCESSING);
        verify(cases).findEnabledForUser(otherId, CaseStatus.ENABLED_FOR_PROCESSING);
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
        for (Role role : List.of(Role.ESTUDIANTE, Role.PROFESOR)) {
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
        List<ResultAvailableEvent> events = new java.util.ArrayList<>();
        var flow = new InternalFlowService(cases, files, assignments, results,
                org.mockito.Mockito.mock(StoragePort.class), org.mockito.Mockito.mock(InternalFlowProperties.class),
                clock, events::add);

        flow.makeResultAvailable(new AuthenticatedUser(professorId, "professor@test", Role.PROFESOR, true), resultId);

        assertEquals(ResultStatus.AVAILABLE, result.getStatus());
        assertEquals(CaseStatus.RESULT_AVAILABLE, medicalCase.getStatus());
        assertEquals(professorId, result.getAvailableBy());
        assertEquals(1, events.size());
        assertEquals(resultId, events.get(0).resultId());
        assertEquals(patientId, events.get(0).patientId());
        assertEquals("patient@test.example", events.get(0).patientEmail());
        assertEquals(professorId, events.get(0).publishedBy());
        assertEquals(ResultAvailableEvent.CURRENT_VERSION, events.get(0).eventVersion());
        assertEquals(ResultAvailableEvent.EVENT_TYPE, events.get(0).eventType());
        assertEquals(patientId, events.get(0).patientId());

        assertThrows(BusinessRuleViolationException.class, () -> flow.makeResultAvailable(
                new AuthenticatedUser(professorId, "professor@test", Role.PROFESOR, true), resultId));
        assertEquals(1, events.size());
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
                new AuthenticatedUser(UUID.randomUUID(), "student@test", Role.ESTUDIANTE, true), resultId));
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
        setId(result, UUID.randomUUID());
        StoragePort storage = org.mockito.Mockito.mock(StoragePort.class);
        when(cases.findById(caseId)).thenReturn(java.util.Optional.of(medicalCase));
        when(results.findByMedicalCaseId(caseId)).thenReturn(java.util.Optional.of(result));
        when(storage.objectExists(resultFile.getObjectKey())).thenReturn(true);
        when(storage.generateDownloadUrl(resultFile.getObjectKey(), resultFile.getContentType(), 7))
                .thenReturn(new StoragePort.UploadUrlResult("https://temporary.test/token", clock.instant().plusSeconds(420)));
        InternalFlowProperties properties = org.mockito.Mockito.mock(InternalFlowProperties.class);
        when(properties.downloadTtlMinutes()).thenReturn(7);
        InternalFlowService service = new InternalFlowService(cases, files, assignments, results, storage, properties, clock);

        var response = service.patientResult(new AuthenticatedUser(patientId, "patient@test", Role.PACIENTE, true), caseId);

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
                new AuthenticatedUser(patientId, "patient@test", Role.PACIENTE, true), caseId));
        verify(storage, never()).generateDownloadUrl(any(), any(), any(Integer.class));
    }

    @Test
    void patientCannotGetResultWhenStoredObjectIsMissing() {
        UUID patientId = UUID.randomUUID();
        UUID caseId = UUID.randomUUID();
        MedicalCaseEntity medicalCase = new MedicalCaseEntity(caseId, patientEntity(patientId), CaseStatus.RESULT_AVAILABLE, "CASE-1", "TAC");
        StoredFileEntity resultFile = new StoredFileEntity(UUID.randomUUID(), medicalCase, "result/missing.pdf", FileKind.RESULT, "missing.pdf", "application/pdf", 10L);
        CaseResultEntity result = new CaseResultEntity(medicalCase, resultFile, ResultStatus.AVAILABLE);
        when(cases.findById(caseId)).thenReturn(java.util.Optional.of(medicalCase));
        when(results.findByMedicalCaseId(caseId)).thenReturn(java.util.Optional.of(result));
        StoragePort storage = org.mockito.Mockito.mock(StoragePort.class);
        when(storage.objectExists(resultFile.getObjectKey())).thenReturn(false);
        InternalFlowProperties properties = org.mockito.Mockito.mock(InternalFlowProperties.class);
        InternalFlowService service = new InternalFlowService(cases, files, assignments, results, storage, properties, clock);

        assertThrows(ResourceNotFoundException.class, () -> service.patientResult(
                new AuthenticatedUser(patientId, "patient@test", Role.PACIENTE, true), caseId));
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
        return new AuthenticatedUser(UUID.randomUUID(), "professor@test", Role.PROFESOR, true);
    }

    private MedicalCaseEntity caseEntity(UUID id, CaseStatus status) {
        return new MedicalCaseEntity(id, org.mockito.Mockito.mock(AppUserEntity.class), status, "CASE-" + id, "TAC");
    }

    private StoredFileEntity file(MedicalCaseEntity medicalCase, FileKind kind) {
        return new StoredFileEntity(UUID.randomUUID(), medicalCase, "input/file.dcm", kind, "file.dcm", "application/dicom", 10L);
    }
}
