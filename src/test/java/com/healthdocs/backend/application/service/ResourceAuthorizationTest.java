package com.healthdocs.backend.application.service;

import com.healthdocs.backend.adapter.out.persistence.CaseAssignmentRepository;
import com.healthdocs.backend.adapter.out.persistence.MedicalCaseRepository;
import com.healthdocs.backend.adapter.out.persistence.StoredFileRepository;
import com.healthdocs.backend.application.port.out.AuthProvider.AuthenticatedUser;
import com.healthdocs.backend.domain.model.Role;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ResourceAuthorizationTest {

    private MedicalCaseRepository cases;
    private CaseAssignmentRepository assignments;
    private StoredFileRepository files;
    private ResourceAuthorization authorization;

    @BeforeEach
    void setUp() {
        cases = mock(MedicalCaseRepository.class);
        assignments = mock(CaseAssignmentRepository.class);
        files = mock(StoredFileRepository.class);
        authorization = new ResourceAuthorization(cases, assignments, files);
    }

    @Test
    void patientCanAccessOnlyOwnedCaseAndFile() {
        UUID patientId = UUID.randomUUID();
        UUID caseId = UUID.randomUUID();
        UUID fileId = UUID.randomUUID();
        AuthenticatedUser patient = new AuthenticatedUser(patientId, "p@x.test", Role.PATIENT, true);
        when(cases.existsByIdAndPatientId(caseId, patientId)).thenReturn(true);
        when(files.existsByIdAndMedicalCaseId(fileId, caseId)).thenReturn(true);

        assertTrue(authorization.canAccessCase(patient, caseId));
        assertTrue(authorization.canAccessFile(patient, caseId, fileId));
        assertFalse(authorization.canAccessCase(patient, UUID.randomUUID()));
    }

    @Test
    void studentRequiresExplicitAssignment() {
        UUID userId = UUID.randomUUID();
        UUID caseId = UUID.randomUUID();
        AuthenticatedUser researcher = new AuthenticatedUser(userId, "s@x.test", Role.RESEARCHER, true);
        when(assignments.existsByCaseIdAndUserId(caseId, userId)).thenReturn(false);
        assertFalse(authorization.canAccessCase(researcher, caseId));
        when(assignments.existsByCaseIdAndUserId(caseId, userId)).thenReturn(true);
        assertTrue(authorization.canAccessCase(researcher, caseId));
    }

    @Test
    void onlyProfessorCanAssignAndPublish() {
        AuthenticatedUser professor = new AuthenticatedUser(
                UUID.randomUUID(), "p@x.test", Role.COORDINATOR, true);
        AuthenticatedUser student = new AuthenticatedUser(
                UUID.randomUUID(), "s@x.test", Role.RESEARCHER, true);
        assertTrue(authorization.canAssignCases(professor));
        assertFalse(authorization.canAssignCases(student));
    }
}
