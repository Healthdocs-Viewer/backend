package com.healthdocs.backend.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.healthdocs.backend.adapter.out.persistence.MedicalCaseRepository;
import com.healthdocs.backend.adapter.out.persistence.StoredFileRepository;
import com.healthdocs.backend.adapter.out.persistence.entity.AppUserEntity;
import com.healthdocs.backend.adapter.out.persistence.entity.MedicalCaseEntity;
import com.healthdocs.backend.adapter.out.persistence.entity.StoredFileEntity;
import com.healthdocs.backend.application.port.out.AuthProvider.AuthenticatedUser;
import com.healthdocs.backend.domain.model.CaseStatus;
import com.healthdocs.backend.domain.model.FileKind;
import com.healthdocs.backend.domain.model.Role;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ListPendingCasesUseCaseTest {

    private static final Instant NOW = Instant.parse("2026-09-17T12:00:00Z");

    @Mock
    private MedicalCaseRepository cases;

    @Mock
    private StoredFileRepository files;

    @Test
    void listsPendingCasesForEnabledProfessor() {
        UUID caseId = UUID.randomUUID();
        AppUserEntity patient = new AppUserEntity("pat", "pat@test.local", Role.PATIENT);
        MedicalCaseEntity caseEntity = new MedicalCaseEntity(
                caseId, patient, CaseStatus.FILES_RECEIVED, "CAS-20260917-0001", "Radiografía de tórax");

        UUID fileId = UUID.randomUUID();
        StoredFileEntity inputFile = new StoredFileEntity(
                fileId, caseEntity, "input/key/rx.dcm", FileKind.INPUT, "rx.dcm", "application/dicom", 1024L);

        when(cases.findPendingCases(CaseStatus.FILES_RECEIVED)).thenReturn(List.of(caseEntity));
        when(files.findByMedicalCaseId(caseId)).thenReturn(List.of(inputFile));

        ListPendingCasesUseCase useCase = new ListPendingCasesUseCase(cases, files);
        List<ListPendingCasesUseCase.PendingCaseView> result = useCase.execute(professor(true));

        assertEquals(1, result.size());
        ListPendingCasesUseCase.PendingCaseView view = result.get(0);
        assertEquals(caseId, view.id());
        assertEquals("CAS-20260917-0001", view.referenceCode());
        assertEquals("Radiografía de tórax", view.studyName());
        assertEquals(CaseStatus.FILES_RECEIVED, view.status());
        assertEquals(1, view.files().size());
        assertEquals(fileId, view.files().get(0).id());
        assertEquals("rx.dcm", view.files().get(0).originalFilename());
        assertEquals(FileKind.INPUT, view.files().get(0).fileKind());

        verify(cases).findPendingCases(CaseStatus.FILES_RECEIVED);
        verify(files).findByMedicalCaseId(caseId);
    }

    @Test
    void filtersOutNonInputFilesFromCaseView() {
        UUID caseId = UUID.randomUUID();
        AppUserEntity patient = new AppUserEntity("pat", "pat@test.local", Role.PATIENT);
        MedicalCaseEntity caseEntity = new MedicalCaseEntity(
                caseId, patient, CaseStatus.FILES_RECEIVED, "CAS-20260917-0001", "Radiografía de tórax");

        UUID inputFileId = UUID.randomUUID();
        StoredFileEntity inputFile = new StoredFileEntity(
                inputFileId, caseEntity, "input/key/rx.dcm", FileKind.INPUT, "rx.dcm", "application/dicom", 1024L);
        StoredFileEntity resultFile = new StoredFileEntity(
                UUID.randomUUID(), caseEntity, "result/key/res.pdf", FileKind.RESULT, "res.pdf", "application/pdf", 2048L);

        when(cases.findPendingCases(CaseStatus.FILES_RECEIVED)).thenReturn(List.of(caseEntity));
        when(files.findByMedicalCaseId(caseId)).thenReturn(List.of(inputFile, resultFile));

        ListPendingCasesUseCase useCase = new ListPendingCasesUseCase(cases, files);
        List<ListPendingCasesUseCase.PendingCaseView> result = useCase.execute(professor(true));

        assertEquals(1, result.size());
        assertEquals(1, result.get(0).files().size());
        assertEquals(inputFileId, result.get(0).files().get(0).id());
        assertEquals(FileKind.INPUT, result.get(0).files().get(0).fileKind());
    }

    @Test
    void returnsEmptyListWhenNoPendingCases() {
        when(cases.findPendingCases(CaseStatus.FILES_RECEIVED)).thenReturn(List.of());

        ListPendingCasesUseCase useCase = new ListPendingCasesUseCase(cases, files);
        List<ListPendingCasesUseCase.PendingCaseView> result = useCase.execute(professor(true));

        assertTrue(result.isEmpty());
    }

    @Test
    void rejectsNullUser() {
        ListPendingCasesUseCase useCase = new ListPendingCasesUseCase(cases, files);
        assertThrows(ForbiddenOperationException.class, () -> useCase.execute(null));
    }

    @Test
    void rejectsDisabledProfessor() {
        ListPendingCasesUseCase useCase = new ListPendingCasesUseCase(cases, files);
        assertThrows(ForbiddenOperationException.class, () -> useCase.execute(professor(false)));
    }

    @Test
    void rejectsStudentAndPatient() {
        ListPendingCasesUseCase useCase = new ListPendingCasesUseCase(cases, files);
        assertThrows(ForbiddenOperationException.class, () -> useCase.execute(user(Role.RESEARCHER, true)));
        assertThrows(ForbiddenOperationException.class, () -> useCase.execute(user(Role.PATIENT, true)));
    }

    private static AuthenticatedUser professor(boolean enabled) {
        return new AuthenticatedUser(UUID.randomUUID(), "prof@healthdocs.local", Role.COORDINATOR, enabled);
    }

    private static AuthenticatedUser user(Role role, boolean enabled) {
        return new AuthenticatedUser(UUID.randomUUID(), "user@healthdocs.local", role, enabled);
    }
}
