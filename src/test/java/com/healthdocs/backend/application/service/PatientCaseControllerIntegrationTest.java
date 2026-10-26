package com.healthdocs.backend.application.service;

import static org.hamcrest.Matchers.startsWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
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
    private CompleteUploadUseCase completeUpload;

    @MockBean
    private ListPatientCasesUseCase listPatientCases;

    @Test
    void listsCasesForAuthenticatedPatient() throws Exception {
        UUID patientId = UUID.randomUUID();
        UUID caseId = UUID.randomUUID();
        when(listPatientCases.execute(any(), any(), any(), any(), anyInt(), anyInt())).thenReturn(
                new ListPatientCasesUseCase.Response(
                        List.of(new ListPatientCasesUseCase.CaseView(
                                caseId, "CAS-001", "TAC", CaseStatus.CONSENT_PENDING, NOW, NOW, null)),
                        0, 10, 1, 1,
                        new ListPatientCasesUseCase.StatusCounts(1, 1, 0, 0),
                        new ListPatientCasesUseCase.AppliedFilters("", "ALL", "createdAt:desc")));

        mockMvc.perform(get("/api/v1/cases")
                        .with(authentication(patientAuth(patientId))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].id").value(caseId.toString()))
                .andExpect(jsonPath("$.items[0].referenceCode").value("CAS-001"));
    }

    @Test
    void rejectsUnauthenticatedCaseList() throws Exception {
        mockMvc.perform(get("/api/v1/cases"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void allowsInternalRolesToListOwnCases() throws Exception {
        mockMvc.perform(get("/api/v1/cases")
                        .with(authentication(authenticatedUser(UUID.randomUUID(), Role.RESEARCHER))))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/cases")
                        .with(authentication(authenticatedUser(UUID.randomUUID(), Role.COORDINATOR))))
                .andExpect(status().isOk());
    }

    @Test
    void createsCaseWithPatientAuthentication() throws Exception {
        UUID patientId = UUID.randomUUID();
        UUID caseId = UUID.randomUUID();
        UUID idempotencyKey = UUID.randomUUID();
        when(createCase.executeIdempotent(any())).thenReturn(new CreateCaseUseCase.CreationResult(new MedicalCase(
                caseId,
                patientId,
                "CAS-20260915-0001",
                "Radiografía de tórax",
                CaseStatus.CONSENT_PENDING,
                NOW,
                NOW), false));

        mockMvc.perform(post("/api/v1/cases")
                        .with(authentication(patientAuth(patientId)))
                        .header("Idempotency-Key", idempotencyKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"studyName\":\"Radiografía de tórax\",\"originInstitution\":\"Centro de prueba\",\"studyDate\":\"2026-09-15\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(caseId.toString()))
                .andExpect(jsonPath("$.referenceCode").value("CAS-20260915-0001"))
                .andExpect(jsonPath("$.status").value("CONSENT_PENDING"))
                .andExpect(jsonPath("$.studyName").value("Radiografía de tórax"))
                .andExpect(header().string("Idempotency-Replayed", "false"));
    }

    @Test
    void idempotentRetryReturnsOkAndMarksReplay() throws Exception {
        UUID patientId = UUID.randomUUID();
        UUID caseId = UUID.randomUUID();
        when(createCase.executeIdempotent(any())).thenReturn(new CreateCaseUseCase.CreationResult(new MedicalCase(
                caseId, patientId, "CAS-20260915-0001", "Radiografía de tórax",
                CaseStatus.CONSENT_PENDING, NOW, NOW), true));

        mockMvc.perform(post("/api/v1/cases")
                        .with(authentication(patientAuth(patientId)))
                        .header("Idempotency-Key", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"studyName\":\"Radiografía de tórax\",\"originInstitution\":\"Centro de prueba\",\"studyDate\":\"2026-09-15\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(caseId.toString()))
                .andExpect(header().string("Idempotency-Replayed", "true"));
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
        UUID fileId = UUID.randomUUID();
        when(completeUpload.execute(any(), any())).thenReturn(new CompleteUploadUseCase.Completion(
                new InternalFlowService.FileView(fileId, "rx.dcm", "application/dicom", 1024L, FileKind.INPUT),
                null,
                CaseStatus.CONFIRMATION_PENDING));

        mockMvc.perform(post("/api/v1/files/{fileId}/complete", fileId)
                        .with(authentication(patientAuth(patientId))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.file.id").value(fileId.toString()))
                .andExpect(jsonPath("$.file.originalFilename").value("rx.dcm"));
    }

    @Test
    void mapsNotFoundToContractError() throws Exception {
        when(completeUpload.execute(any(), any())).thenThrow(new ResourceNotFoundException("File not found"));

        mockMvc.perform(post("/api/v1/files/{fileId}/complete", UUID.randomUUID())
                        .with(authentication(patientAuth(UUID.randomUUID()))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"))
                .andExpect(jsonPath("$.traceId").exists());
    }

    @Test
    void allowsResearcherRoleToCreateCase() throws Exception {
        UUID researcherId = UUID.randomUUID();
        when(createCase.executeIdempotent(any())).thenReturn(new CreateCaseUseCase.CreationResult(new MedicalCase(
                UUID.randomUUID(), researcherId, "CAS-20260915-0002", "Radiografía de tórax",
                CaseStatus.CONSENT_PENDING, NOW, NOW), false));

        mockMvc.perform(post("/api/v1/cases")
                        .with(authentication(authenticatedUser(researcherId, Role.RESEARCHER)))
                        .header("Idempotency-Key", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"studyName\":\"Radiografía de tórax\",\"originInstitution\":\"Centro de prueba\",\"studyDate\":\"2026-09-15\"}"))
                .andExpect(status().isCreated());
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
        return authenticatedUser(patientId, Role.PATIENT);
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
