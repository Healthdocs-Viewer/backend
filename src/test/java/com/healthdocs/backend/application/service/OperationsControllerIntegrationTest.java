package com.healthdocs.backend.application.service;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.healthdocs.backend.adapter.out.persistence.AppUserRepository;
import com.healthdocs.backend.adapter.out.persistence.CaseResultRepository;
import com.healthdocs.backend.adapter.out.persistence.MedicalCaseRepository;
import com.healthdocs.backend.adapter.out.persistence.StoredFileRepository;
import com.healthdocs.backend.adapter.out.persistence.entity.AppUserEntity;
import com.healthdocs.backend.adapter.out.persistence.entity.CaseResultEntity;
import com.healthdocs.backend.adapter.out.persistence.entity.MedicalCaseEntity;
import com.healthdocs.backend.adapter.out.persistence.entity.StoredFileEntity;
import com.healthdocs.backend.application.port.out.AuthProvider.AuthenticatedUser;
import com.healthdocs.backend.domain.model.CaseStatus;
import com.healthdocs.backend.domain.model.FileKind;
import com.healthdocs.backend.domain.model.ResultStatus;
import com.healthdocs.backend.domain.model.Role;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class OperationsControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AppUserRepository users;

    @Autowired
    private MedicalCaseRepository cases;

    @Autowired
    private StoredFileRepository files;

    @Autowired
    private CaseResultRepository results;

    private AppUserEntity patient;
    private AppUserEntity professor;

    @BeforeEach
    void setUp() {
        patient = users.save(new AppUserEntity("patient-" + UUID.randomUUID(), "patient-" + UUID.randomUUID() + "@healthdocs.test", Role.PATIENT));
        professor = users.save(new AppUserEntity("prof-" + UUID.randomUUID(), "prof-" + UUID.randomUUID() + "@healthdocs.test", Role.COORDINATOR));
    }

    @Test
    void rejectsUnauthenticatedPendingCasesList() throws Exception {
        mockMvc.perform(get("/api/v1/operations/cases/pending"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void rejectsNonProfessorRolesFromPendingCasesList() throws Exception {
        mockMvc.perform(get("/api/v1/operations/cases/pending")
                        .with(authentication(authenticatedUser(UUID.randomUUID(), Role.RESEARCHER, true))))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/v1/operations/cases/pending")
                        .with(authentication(authenticatedUser(UUID.randomUUID(), Role.PATIENT, true))))
                .andExpect(status().isForbidden());
    }

    @Test
    void rejectsDisabledProfessorFromPendingCasesList() throws Exception {
        mockMvc.perform(get("/api/v1/operations/cases/pending")
                        .with(authentication(authenticatedUser(professor.getId(), Role.COORDINATOR, false))))
                .andExpect(status().isForbidden());
    }

    @Test
    void returnsEmptyListWhenNoCasesMeetCriteria() throws Exception {
        mockMvc.perform(get("/api/v1/operations/cases/pending")
                        .with(authentication(authenticatedUser(professor.getId(), Role.COORDINATOR, true))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void listsPendingCasesWithInputFilesAndExcludesIneligibleCases() throws Exception {
        // Case 1: FILES_RECEIVED with INPUT file -> Should be returned
        MedicalCaseEntity eligibleCase = cases.save(new MedicalCaseEntity(
                UUID.randomUUID(), patient, CaseStatus.FILES_RECEIVED, "CAS-ELIGIBLE-001", "Radiografía de tórax"));
        StoredFileEntity inputFile = files.save(new StoredFileEntity(
                UUID.randomUUID(), eligibleCase, "input/key/rx.dcm", FileKind.INPUT, "rx.dcm", "application/dicom", 1024L));

        // Case 2: CREATED with no files -> Should NOT be returned
        cases.save(new MedicalCaseEntity(
                UUID.randomUUID(), patient, CaseStatus.CREATED, "CAS-CREATED-002", "TAC_CRANEO"));

        // Case 3: FILES_RECEIVED with NO files -> Should NOT be returned
        cases.save(new MedicalCaseEntity(
                UUID.randomUUID(), patient, CaseStatus.FILES_RECEIVED, "CAS-NO-FILES-003", "RESONANCIA"));

        // Case 4: ENABLED_FOR_PROCESSING with INPUT file -> Should NOT be returned
        MedicalCaseEntity enabledCase = cases.save(new MedicalCaseEntity(
                UUID.randomUUID(), patient, CaseStatus.ENABLED_FOR_PROCESSING, "CAS-ENABLED-004", "ECOGRAFIA"));
        files.save(new StoredFileEntity(
                UUID.randomUUID(), enabledCase, "input/key/eco.dcm", FileKind.INPUT, "eco.dcm", "application/dicom", 2048L));

        // Case 5: FILES_RECEIVED with INPUT file AND Result -> Should NOT be returned
        MedicalCaseEntity caseWithResult = cases.save(new MedicalCaseEntity(
                UUID.randomUUID(), patient, CaseStatus.FILES_RECEIVED, "CAS-WITH-RESULT-005", "TAC_TORAX"));
        StoredFileEntity input5 = files.save(new StoredFileEntity(
                UUID.randomUUID(), caseWithResult, "input/key/tac.dcm", FileKind.INPUT, "tac.dcm", "application/dicom", 4096L));
        StoredFileEntity resultFile = files.save(new StoredFileEntity(
                UUID.randomUUID(), caseWithResult, "result/key/res.pdf", FileKind.RESULT, "res.pdf", "application/pdf", 512L));
        results.save(new CaseResultEntity(caseWithResult, resultFile, ResultStatus.UPLOADED));

        mockMvc.perform(get("/api/v1/operations/cases/pending")
                        .with(authentication(authenticatedUser(professor.getId(), Role.COORDINATOR, true))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id").value(eligibleCase.getId().toString()))
                .andExpect(jsonPath("$[0].referenceCode").value("CAS-ELIGIBLE-001"))
                .andExpect(jsonPath("$[0].studyName").value("Radiografía de tórax"))
                .andExpect(jsonPath("$[0].status").value("FILES_RECEIVED"))
                .andExpect(jsonPath("$[0].createdAt").exists())
                .andExpect(jsonPath("$[0].files", hasSize(1)))
                .andExpect(jsonPath("$[0].files[0].id").value(inputFile.getId().toString()))
                .andExpect(jsonPath("$[0].files[0].originalFilename").value("rx.dcm"))
                .andExpect(jsonPath("$[0].files[0].contentType").value("application/dicom"))
                .andExpect(jsonPath("$[0].files[0].sizeBytes").value(1024))
                .andExpect(jsonPath("$[0].files[0].fileKind").value("INPUT"))
                // Ensure patientId is not exposed
                .andExpect(jsonPath("$[0].patientId").doesNotExist())
                .andExpect(jsonPath("$[0].patient").doesNotExist());
    }

    private static UsernamePasswordAuthenticationToken authenticatedUser(UUID userId, Role role, boolean enabled) {
        AuthenticatedUser principal = new AuthenticatedUser(userId, "user@healthdocs.local", role, enabled);
        return new UsernamePasswordAuthenticationToken(
                principal,
                null,
                enabled
                        ? List.of(
                                new SimpleGrantedAuthority("ROLE_" + role.name()),
                                new SimpleGrantedAuthority("ACCOUNT_ENABLED"))
                        : List.of(new SimpleGrantedAuthority("ACCOUNT_DISABLED")));
    }
}
