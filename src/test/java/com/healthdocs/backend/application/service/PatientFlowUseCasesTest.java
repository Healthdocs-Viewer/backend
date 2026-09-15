package com.healthdocs.backend.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.healthdocs.backend.application.port.out.CaseRepositoryPort;
import com.healthdocs.backend.application.port.out.StoragePort;
import com.healthdocs.backend.domain.model.CaseConsent;
import com.healthdocs.backend.domain.model.CaseStatus;
import com.healthdocs.backend.domain.model.FileKind;
import com.healthdocs.backend.domain.model.MedicalCase;
import com.healthdocs.backend.domain.model.Role;
import com.healthdocs.backend.domain.model.StoredFile;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PatientFlowUseCasesTest {

    private static final Instant NOW = Instant.parse("2026-09-15T21:06:00Z");

    private InMemoryCases cases;
    private FakeStorage storage;
    private CreateCaseUseCase createCase;
    private AcceptTermsUseCase acceptTerms;
    private GenerateUploadUrlUseCase generateUploadUrl;
    private ConfirmFileUploadUseCase confirmFileUpload;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        cases = new InMemoryCases();
        storage = new FakeStorage(clock);
        createCase = new CreateCaseUseCase(cases, clock);
        acceptTerms = new AcceptTermsUseCase(cases, clock);
        generateUploadUrl = new GenerateUploadUrlUseCase(cases, storage, clock);
        confirmFileUpload = new ConfirmFileUploadUseCase(cases, storage, clock);
    }

    @Test
    void createsCaseWithOperationalMetadata() {
        UUID patientId = UUID.randomUUID();

        MedicalCase medicalCase = createCase.execute(
                new CreateCaseUseCase.Command(patientId, Role.PACIENTE, true, "RADIOGRAFIA_TORAX"));

        assertEquals(patientId, medicalCase.patientId());
        assertEquals("CAS-20260915-0001", medicalCase.referenceCode());
        assertEquals("RADIOGRAFIA_TORAX", medicalCase.studyType());
        assertEquals(CaseStatus.CREATED, medicalCase.status());
    }

    @Test
    void acceptsTermsOnlyForOwnedCases() {
        UUID ownerId = UUID.randomUUID();
        MedicalCase medicalCase = createCase.execute(
                new CreateCaseUseCase.Command(ownerId, Role.PACIENTE, true, "RADIOGRAFIA_TORAX"));

        CaseConsent consent = acceptTerms.execute(
                new AcceptTermsUseCase.Command(ownerId, Role.PACIENTE, true, medicalCase.id(), "1.0"));

        assertEquals(ownerId, consent.patientId());
        assertThrows(
                ForbiddenOperationException.class,
                () -> acceptTerms.execute(new AcceptTermsUseCase.Command(
                        UUID.randomUUID(), Role.PACIENTE, true, medicalCase.id(), "1.0")));
    }

    @Test
    void requiresConsentBeforeGeneratingUploadUrl() {
        UUID patientId = UUID.randomUUID();
        MedicalCase medicalCase = createCase.execute(
                new CreateCaseUseCase.Command(patientId, Role.PACIENTE, true, "RADIOGRAFIA_TORAX"));

        assertThrows(
                BusinessRuleViolationException.class,
                () -> generateUploadUrl.execute(new GenerateUploadUrlUseCase.Command(
                        patientId,
                        Role.PACIENTE,
                        true,
                        medicalCase.id(),
                        "radiografia.dcm",
                        "application/dicom",
                        1024L)));
    }

    @Test
    void generatesUploadAndConfirmTransitionsCaseWhenObjectExists() {
        UUID patientId = UUID.randomUUID();
        MedicalCase medicalCase = createCase.execute(
                new CreateCaseUseCase.Command(patientId, Role.PACIENTE, true, "RADIOGRAFIA_TORAX"));
        acceptTerms.execute(new AcceptTermsUseCase.Command(patientId, Role.PACIENTE, true, medicalCase.id(), "1.0"));

        GenerateUploadUrlUseCase.Result upload = generateUploadUrl.execute(
                new GenerateUploadUrlUseCase.Command(
                        patientId,
                        Role.PACIENTE,
                        true,
                        medicalCase.id(),
                        "folder/radiografia.dcm",
                        "application/dicom",
                        1024L));
        storage.put(upload.file().objectKey());

        StoredFile confirmed = confirmFileUpload.execute(
                new ConfirmFileUploadUseCase.Command(patientId, Role.PACIENTE, true, upload.file().id()));

        assertEquals(upload.file().id(), confirmed.id());
        assertTrue(confirmed.objectKey().endsWith("_radiografia.dcm"));
        assertEquals(CaseStatus.FILES_RECEIVED, cases.findCaseById(medicalCase.id()).orElseThrow().status());
    }

    @Test
    void patientCannotConfirmAnotherPatientsFile() {
        UUID patientId = UUID.randomUUID();
        MedicalCase medicalCase = createCase.execute(
                new CreateCaseUseCase.Command(patientId, Role.PACIENTE, true, "RADIOGRAFIA_TORAX"));
        acceptTerms.execute(new AcceptTermsUseCase.Command(patientId, Role.PACIENTE, true, medicalCase.id(), "1.0"));
        GenerateUploadUrlUseCase.Result upload = generateUploadUrl.execute(
                new GenerateUploadUrlUseCase.Command(
                        patientId, Role.PACIENTE, true, medicalCase.id(), "rx.dcm", "application/dicom", 1024L));
        storage.put(upload.file().objectKey());

        assertThrows(
                ForbiddenOperationException.class,
                () -> confirmFileUpload.execute(new ConfirmFileUploadUseCase.Command(
                        UUID.randomUUID(), Role.PACIENTE, true, upload.file().id())));
    }

    private static class InMemoryCases implements CaseRepositoryPort {
        private final Map<UUID, MedicalCase> cases = new HashMap<>();
        private final Map<UUID, StoredFile> files = new HashMap<>();
        private final Map<String, CaseConsent> consents = new HashMap<>();

        @Override
        public MedicalCase saveCase(MedicalCase medicalCase) {
            cases.put(medicalCase.id(), medicalCase);
            return medicalCase;
        }

        @Override
        public Optional<MedicalCase> findCaseById(UUID caseId) {
            return Optional.ofNullable(cases.get(caseId));
        }

        @Override
        public boolean existsCaseByIdAndPatientId(UUID caseId, UUID patientId) {
            return findCaseById(caseId).map(c -> c.patientId().equals(patientId)).orElse(false);
        }

        @Override
        public long countCasesCreatedOn(LocalDate date) {
            return cases.values().stream()
                    .filter(c -> LocalDate.ofInstant(c.createdAt(), ZoneOffset.UTC).equals(date))
                    .count();
        }

        @Override
        public CaseConsent saveConsent(CaseConsent consent) {
            consents.put(consent.caseId() + ":" + consent.termsVersion(), consent);
            return consent;
        }

        @Override
        public boolean existsConsent(UUID caseId, String termsVersion) {
            return consents.containsKey(caseId + ":" + termsVersion);
        }

        @Override
        public boolean hasAnyConsent(UUID caseId) {
            return consents.values().stream().anyMatch(c -> c.caseId().equals(caseId));
        }

        @Override
        public StoredFile saveFile(StoredFile file) {
            files.put(file.id(), file);
            return file;
        }

        @Override
        public Optional<StoredFile> findFileById(UUID fileId) {
            return Optional.ofNullable(files.get(fileId));
        }

        @Override
        public long countFilesByCaseIdAndKind(UUID caseId, FileKind fileKind) {
            return files.values().stream()
                    .filter(f -> f.caseId().equals(caseId) && f.fileKind() == fileKind)
                    .count();
        }
    }

    private static class FakeStorage implements StoragePort {
        private final Clock clock;
        private final Map<String, Boolean> objects = new HashMap<>();

        private FakeStorage(Clock clock) {
            this.clock = clock;
        }

        @Override
        public UploadUrlResult generateUploadUrl(String objectKey, String contentType, int ttlMinutes) {
            return new UploadUrlResult(
                    "http://localhost:8080/api/v1/dev/uploads/" + objectKey,
                    clock.instant().plusSeconds(ttlMinutes * 60L));
        }

        @Override
        public boolean objectExists(String objectKey) {
            return objects.containsKey(objectKey);
        }

        void put(String objectKey) {
            objects.put(objectKey, true);
        }
    }
}
