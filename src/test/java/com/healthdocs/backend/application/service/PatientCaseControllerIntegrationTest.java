package com.healthdocs.backend.application.service;

import static org.hamcrest.Matchers.startsWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.healthdocs.backend.adapter.out.storage.LocalStorageAdapter;
import com.healthdocs.backend.application.port.out.AuthProvider.AuthenticatedUser;
import com.healthdocs.backend.application.port.out.StoragePort;
import com.healthdocs.backend.domain.model.CaseConsent;
import com.healthdocs.backend.domain.model.CaseStatus;
import com.healthdocs.backend.domain.model.FileKind;
import com.healthdocs.backend.domain.model.MedicalCase;
import com.healthdocs.backend.domain.model.Role;
import com.healthdocs.backend.domain.model.StoredFile;
import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PatientCaseControllerIntegrationTest {

    private static final Instant NOW = Instant.parse("2026-09-15T21:06:00Z");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private LocalStorageAdapter localStorage;

    @MockBean
    private CreateCaseUseCase createCase;

    @MockBean
    private AcceptTermsUseCase acceptTerms;

    @MockBean
    private GenerateUploadUrlUseCase generateUploadUrl;

    @MockBean
    private ConfirmFileUploadUseCase confirmFileUpload;

    @MockBean
    private ListPatientCasesUseCase listPatientCases;

    @Test
    void listsCasesForAuthenticatedPatient() throws Exception {
        UUID patientId = UUID.randomUUID();
        UUID caseId = UUID.randomUUID();
        when(listPatientCases.execute(any())).thenReturn(List.of(
                new ListPatientCasesUseCase.CaseView(
                        caseId, "CAS-001", "TAC", CaseStatus.CREATED, NOW)));

        mockMvc.perform(get("/api/v1/cases")
                        .with(authentication(patientAuth(patientId))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(caseId.toString()))
                .andExpect(jsonPath("$[0].referenceCode").value("CAS-001"));
    }

    @Test
    void rejectsUnauthenticatedCaseList() throws Exception {
        mockMvc.perform(get("/api/v1/cases"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void rejectsInternalRolesFromCaseList() throws Exception {
        mockMvc.perform(get("/api/v1/cases")
                        .with(authentication(authenticatedUser(UUID.randomUUID(), Role.ESTUDIANTE))))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/cases")
                        .with(authentication(authenticatedUser(UUID.randomUUID(), Role.PROFESOR))))
                .andExpect(status().isForbidden());
    }

    @Test
    void createsCaseWithPatientAuthentication() throws Exception {
        UUID patientId = UUID.randomUUID();
        UUID caseId = UUID.randomUUID();
        when(createCase.execute(any())).thenReturn(new MedicalCase(
                caseId,
                patientId,
                "CAS-20260915-0001",
                "RADIOGRAFIA_TORAX",
                CaseStatus.CREATED,
                NOW,
                NOW));

        mockMvc.perform(post("/api/v1/cases")
                        .with(authentication(patientAuth(patientId)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"studyType\":\"RADIOGRAFIA_TORAX\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(caseId.toString()))
                .andExpect(jsonPath("$.referenceCode").value("CAS-20260915-0001"))
                .andExpect(jsonPath("$.status").value("CREATED"))
                .andExpect(jsonPath("$.studyType").value("RADIOGRAFIA_TORAX"));
    }

    @Test
    void acceptsConsent() throws Exception {
        UUID patientId = UUID.randomUUID();
        UUID caseId = UUID.randomUUID();
        UUID consentId = UUID.randomUUID();
        when(acceptTerms.execute(any())).thenReturn(new CaseConsent(consentId, caseId, patientId, "1.0", NOW));

        mockMvc.perform(post("/api/v1/cases/{caseId}/consent", caseId)
                        .with(authentication(patientAuth(patientId)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"termsVersion\":\"1.0\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(consentId.toString()))
                .andExpect(jsonPath("$.caseId").value(caseId.toString()))
                .andExpect(jsonPath("$.termsVersion").value("1.0"));
    }

    @Test
    void generatesUploadUrl() throws Exception {
        UUID patientId = UUID.randomUUID();
        UUID caseId = UUID.randomUUID();
        StoredFile file = new StoredFile(
                UUID.randomUUID(),
                caseId,
                "input/%s/file/rx.dcm".formatted(caseId),
                FileKind.INPUT,
                "rx.dcm",
                "application/dicom",
                1024L,
                NOW,
                NOW);
        when(generateUploadUrl.execute(any())).thenReturn(new GenerateUploadUrlUseCase.Result(
                file,
                "http://localhost:8080/api/v1/dev/uploads/" + file.objectKey() + "?token=abc",
                NOW.plusSeconds(900)));

        mockMvc.perform(post("/api/v1/cases/{caseId}/uploads", caseId)
                        .with(authentication(patientAuth(patientId)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "originalFilename": "rx.dcm",
                                  "contentType": "application/dicom",
                                  "sizeBytes": 1024
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.file.id").value(file.id().toString()))
                .andExpect(jsonPath("$.file.fileKind").value("INPUT"))
                .andExpect(jsonPath("$.uploadUrl").value(startsWith("http://localhost:8080/api/v1/dev/uploads/")));
    }

    @Test
    void confirmsUpload() throws Exception {
        UUID patientId = UUID.randomUUID();
        UUID caseId = UUID.randomUUID();
        UUID fileId = UUID.randomUUID();
        when(confirmFileUpload.execute(any())).thenReturn(new StoredFile(
                fileId, caseId, "input/key", FileKind.INPUT, "rx.dcm", "application/dicom", 1024L, NOW, NOW));

        mockMvc.perform(post("/api/v1/files/{fileId}/complete", fileId)
                        .with(authentication(patientAuth(patientId))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.file.id").value(fileId.toString()))
                .andExpect(jsonPath("$.file.originalFilename").value("rx.dcm"));
    }

    @Test
    void mapsNotFoundToContractError() throws Exception {
        when(confirmFileUpload.execute(any())).thenThrow(new ResourceNotFoundException("File not found"));

        mockMvc.perform(post("/api/v1/files/{fileId}/complete", UUID.randomUUID())
                        .with(authentication(patientAuth(UUID.randomUUID()))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"))
                .andExpect(jsonPath("$.traceId").exists());
    }

    @Test
    void rejectsNonPatientRoleByPreAuthorize() throws Exception {
        mockMvc.perform(post("/api/v1/cases")
                        .with(authentication(authenticatedUser(UUID.randomUUID(), Role.ESTUDIANTE)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"studyType\":\"RADIOGRAFIA_TORAX\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void localDevUploadStoresObjectBehindTemporaryUrl() throws Exception {
        String objectKey = "input/%s/%s/rx.dcm".formatted(UUID.randomUUID(), UUID.randomUUID());
        StoragePort.UploadUrlResult uploadUrl =
                localStorage.generateUploadUrl(objectKey, "application/dicom", 15);
        URI uri = URI.create(uploadUrl.uploadUrl());

        mockMvc.perform(put(uri.getRawPath() + "?" + uri.getRawQuery())
                        .contentType("application/dicom")
                        .content("dicom-bytes"))
                .andExpect(status().isOk());

        org.junit.jupiter.api.Assertions.assertTrue(localStorage.objectExists(objectKey));
    }

    @Test
    void localDevUploadRejectsInvalidToken() throws Exception {
        mockMvc.perform(put("/api/v1/dev/uploads/input/case/file/rx.dcm?token=wrong")
                        .contentType("application/dicom")
                        .content("dicom-bytes"))
                .andExpect(status().isForbidden());
    }

    @Test
    void localDevDownloadReturnsStoredBytesAndRejectsExpiredGrant() throws Exception {
        String objectKey = "result/%s/%s".formatted(UUID.randomUUID(), UUID.randomUUID());
        StoragePort.UploadUrlResult uploadUrl = localStorage.generateUploadUrl(objectKey, "application/pdf", 15);
        URI uploadUri = URI.create(uploadUrl.uploadUrl());

        mockMvc.perform(put(uploadUri.getRawPath() + "?" + uploadUri.getRawQuery())
                        .contentType(MediaType.APPLICATION_PDF)
                        .content("resultado-local"))
                .andExpect(status().isOk());

        StoragePort.UploadUrlResult downloadUrl = localStorage.generateDownloadUrl(objectKey, "application/pdf", 10);
        URI downloadUri = URI.create(downloadUrl.uploadUrl());
        mockMvc.perform(get(downloadUri.getRawPath() + "?" + downloadUri.getRawQuery()))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content()
                        .bytes("resultado-local".getBytes(java.nio.charset.StandardCharsets.UTF_8)))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header()
                        .string(org.springframework.http.HttpHeaders.CONTENT_DISPOSITION,
                                org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("resultado.pdf"))));

        StoragePort.UploadUrlResult expired = localStorage.generateDownloadUrl(objectKey, "application/pdf", 0);
        URI expiredUri = URI.create(expired.uploadUrl());
        mockMvc.perform(get(expiredUri.getRawPath() + "?" + expiredUri.getRawQuery()))
                .andExpect(status().isNotFound());
    }

    private static UsernamePasswordAuthenticationToken patientAuth(UUID patientId) {
        return authenticatedUser(patientId, Role.PACIENTE);
    }

    private static UsernamePasswordAuthenticationToken authenticatedUser(UUID userId, Role role) {
        AuthenticatedUser principal = new AuthenticatedUser(userId, "user@healthdocs.local", role, true);
        return new UsernamePasswordAuthenticationToken(
                principal,
                null,
                List.of(
                        new SimpleGrantedAuthority("ROLE_" + role.name()),
                        new SimpleGrantedAuthority("ACCOUNT_ENABLED")));
    }
}
