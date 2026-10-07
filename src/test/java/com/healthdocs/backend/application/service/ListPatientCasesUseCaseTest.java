package com.healthdocs.backend.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.healthdocs.backend.application.port.out.AuthProvider.AuthenticatedUser;
import com.healthdocs.backend.application.port.out.CaseRepositoryPort;
import com.healthdocs.backend.domain.model.CaseStatus;
import com.healthdocs.backend.domain.model.MedicalCase;
import com.healthdocs.backend.domain.model.Role;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ListPatientCasesUseCaseTest {

    private static final Instant NOW = Instant.parse("2026-09-16T12:00:00Z");

    @Mock
    private CaseRepositoryPort cases;

    @Test
    void listsOnlyCasesForAuthenticatedPatient() {
        UUID patientId = UUID.randomUUID();
        MedicalCase ownCase = MedicalCase.create(UUID.randomUUID(), patientId, "CAS-001", "TAC", NOW);
        when(cases.findAllByPatientId(patientId)).thenReturn(List.of(ownCase));

        List<ListPatientCasesUseCase.CaseView> result =
                new ListPatientCasesUseCase(cases).execute(patient(patientId));

        assertEquals(List.of(ownCase.id()), result.stream().map(ListPatientCasesUseCase.CaseView::id).toList());
        verify(cases).findAllByPatientId(patientId);
    }

    @Test
    void rejectsStudentAndProfessor() {
        ListPatientCasesUseCase useCase = new ListPatientCasesUseCase(cases);

        assertThrows(ForbiddenOperationException.class,
                () -> useCase.execute(user(Role.RESEARCHER)));
        assertThrows(ForbiddenOperationException.class,
                () -> useCase.execute(user(Role.COORDINATOR)));
    }

    private static AuthenticatedUser patient(UUID id) {
        return new AuthenticatedUser(id, "patient@healthdocs.local", Role.PATIENT, true);
    }

    private static AuthenticatedUser user(Role role) {
        return new AuthenticatedUser(UUID.randomUUID(), "user@healthdocs.local", role, true);
    }
}
