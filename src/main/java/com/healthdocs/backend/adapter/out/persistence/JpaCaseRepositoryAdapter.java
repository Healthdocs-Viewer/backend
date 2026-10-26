package com.healthdocs.backend.adapter.out.persistence;

import com.healthdocs.backend.adapter.out.persistence.entity.AppUserEntity;
import com.healthdocs.backend.adapter.out.persistence.entity.CaseConsentEntity;
import com.healthdocs.backend.adapter.out.persistence.entity.MedicalCaseEntity;
import com.healthdocs.backend.adapter.out.persistence.entity.StoredFileEntity;
import com.healthdocs.backend.application.port.out.CaseRepositoryPort;
import com.healthdocs.backend.application.service.ResourceNotFoundException;
import com.healthdocs.backend.domain.model.CaseConsent;
import com.healthdocs.backend.domain.model.CaseStatus;
import com.healthdocs.backend.domain.model.FileKind;
import com.healthdocs.backend.domain.model.MedicalCase;
import com.healthdocs.backend.domain.model.StoredFile;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import java.util.List;
import org.springframework.stereotype.Component;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

@Component
public class JpaCaseRepositoryAdapter implements CaseRepositoryPort {

    private final MedicalCaseRepository cases;
    private final StoredFileRepository files;
    private final CaseConsentRepository consents;
    private final AppUserRepository users;
    private final CaseResultRepository results;
    private final CaseCreationIdempotencyRepository caseCreations;
    private final CaseReferenceCounterRepository referenceCounters;

    @PersistenceContext
    private EntityManager entityManager;

    public JpaCaseRepositoryAdapter(
            MedicalCaseRepository cases,
            StoredFileRepository files,
            CaseConsentRepository consents,
            AppUserRepository users,
            CaseResultRepository results,
            CaseCreationIdempotencyRepository caseCreations,
            CaseReferenceCounterRepository referenceCounters) {
        this.cases = cases;
        this.files = files;
        this.consents = consents;
        this.users = users;
        this.results = results;
        this.caseCreations = caseCreations;
        this.referenceCounters = referenceCounters;
    }

    @Override
    public MedicalCase saveCase(MedicalCase medicalCase) {
        AppUserEntity patient = users.findById(medicalCase.patientId())
                .orElseThrow(() -> new ResourceNotFoundException("Patient not found"));
        Optional<MedicalCaseEntity> existing = cases.findById(medicalCase.id());
        MedicalCaseEntity entity = existing.orElseGet(() -> new MedicalCaseEntity(
                medicalCase.id(),
                patient,
                medicalCase.status(),
                medicalCase.referenceCode(),
                medicalCase.studyName()));
        entity.setCaseDetails(medicalCase.additionalReference(), medicalCase.originInstitution(),
                medicalCase.studyDate(), medicalCase.notes());
        entity.setStatus(medicalCase.status());
        entity.setUpdatedAt(medicalCase.updatedAt());
        entity.setReceivedAt(medicalCase.receivedAt());
        if (existing.isPresent()) {
            return toDomain(cases.save(entity));
        }
        entityManager.persist(entity);
        entityManager.flush();
        return toDomain(entity);
    }

    @Override
    public Optional<MedicalCase> findCaseById(UUID caseId) {
        return cases.findById(caseId).map(this::toDomain);
    }

    @Override
    public Optional<MedicalCase> findCaseByIdForUpdate(UUID caseId) {
        return cases.findByIdForUpdate(caseId).map(this::toDomain);
    }

    @Override
    public Optional<UUID> findAvailableResultId(UUID caseId) {
        return results.findByMedicalCaseId(caseId)
                .filter(result -> result.getStatus() == com.healthdocs.backend.domain.model.ResultStatus.AVAILABLE)
                .map(com.healthdocs.backend.adapter.out.persistence.entity.CaseResultEntity::getId);
    }

    @Override
    public List<MedicalCase> findAllByPatientId(UUID patientId) {
        return cases.findAllByPatientIdOrderByCreatedAtDesc(patientId).stream()
                .map(this::toDomain)
                .toList();
    }

    @Override
    public List<MedicalCase> findAllAssignedInternalCases(UUID userId, List<CaseStatus> statuses) {
        return cases.findAssignedInternalCases(userId, statuses).stream()
                .map(this::toDomain)
                .toList();
    }

    @Override
    public boolean existsCaseByIdAndPatientId(UUID caseId, UUID patientId) {
        return cases.existsByIdAndPatientId(caseId, patientId);
    }

    @Override
    public long countCasesCreatedOn(LocalDate date) {
        Instant start = date.atStartOfDay().toInstant(ZoneOffset.UTC);
        Instant end = date.plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC);
        return cases.countByCreatedAtGreaterThanEqualAndCreatedAtLessThan(start, end);
    }

    @Override
    public void lockCaseCreation(UUID patientId) {
        users.findByIdForCaseCreation(patientId)
                .orElseThrow(() -> new ResourceNotFoundException("Patient not found"));
    }

    @Override
    public Optional<CaseCreationRequest> findCaseCreationRequest(UUID patientId, UUID idempotencyKey) {
        return caseCreations.findByPatient_IdAndIdempotencyKey(patientId, idempotencyKey)
                .map(record -> new CaseCreationRequest(record.getRequestHash(), record.getCaseId()));
    }

    @Override
    public void saveCaseCreationRequest(UUID patientId, UUID idempotencyKey, String requestHash, UUID caseId) {
        AppUserEntity patient = users.findById(patientId)
                .orElseThrow(() -> new ResourceNotFoundException("Patient not found"));
        MedicalCaseEntity medicalCase = cases.findById(caseId)
                .orElseThrow(() -> new ResourceNotFoundException("Case not found"));
        caseCreations.saveAndFlush(new com.healthdocs.backend.adapter.out.persistence.entity.CaseCreationIdempotencyEntity(
                UUID.randomUUID(), patient, idempotencyKey, requestHash, medicalCase));
    }

    @Override
    public long nextCaseReferenceNumber(LocalDate date) {
        referenceCounters.lockSequence();
        Optional<com.healthdocs.backend.adapter.out.persistence.entity.CaseReferenceCounterEntity> counter =
                referenceCounters.findById(date);
        if (counter.isPresent()) {
            var existing = counter.get();
            existing.next();
            return referenceCounters.saveAndFlush(existing).getCurrentValue();
        }
        String prefix = "CAS-" + java.time.format.DateTimeFormatter.BASIC_ISO_DATE.format(date) + "-";
        long initial = cases.maxReferenceNumberWithPrefix(prefix) + 1;
        referenceCounters.saveAndFlush(
                new com.healthdocs.backend.adapter.out.persistence.entity.CaseReferenceCounterEntity(date, initial));
        return initial;
    }

    @Override
    public CaseConsent saveConsent(CaseConsent consent) {
        MedicalCaseEntity medicalCase = cases.findById(consent.caseId())
                .orElseThrow(() -> new ResourceNotFoundException("Case not found"));
        AppUserEntity patient = users.findById(consent.patientId())
                .orElseThrow(() -> new ResourceNotFoundException("Patient not found"));
        Optional<CaseConsentEntity> existing = consents.findById(consent.id());
        CaseConsentEntity entity = existing.orElseGet(() -> new CaseConsentEntity(
                consent.id(), medicalCase, patient, consent.termsVersion(), consent.acceptedAt()));
        if (existing.isPresent()) {
            return toDomain(consents.save(entity));
        }
        entityManager.persist(entity);
        entityManager.flush();
        return toDomain(entity);
    }

    @Override
    public boolean existsConsent(UUID caseId, String termsVersion) {
        return consents.existsByMedicalCaseIdAndTermsVersion(caseId, termsVersion);
    }

    @Override
    public boolean hasAnyConsent(UUID caseId) {
        return consents.existsByMedicalCaseId(caseId);
    }

    @Override
    public StoredFile saveFile(StoredFile file) {
        MedicalCaseEntity medicalCase = cases.findById(file.caseId())
                .orElseThrow(() -> new ResourceNotFoundException("Case not found"));
        Optional<StoredFileEntity> existing = files.findById(file.id());
        StoredFileEntity entity = existing.orElseGet(() -> new StoredFileEntity(
                        file.id(),
                        medicalCase,
                        file.objectKey(),
                        file.fileKind(),
                        file.originalFilename(),
                        file.contentType(),
                        file.sizeBytes(),
                        file.uploadInitiatedBy()));
        entity.setUpdatedAt(file.updatedAt());
        entity.setUploadInitiatedBy(file.uploadInitiatedBy());
        entity.setUploadExpiresAt(file.uploadExpiresAt());
        if (file.confirmedAt() != null) entity.confirm(file.confirmedAt());
        if (existing.isPresent()) {
            return toDomain(files.save(entity));
        }
        entityManager.persist(entity);
        entityManager.flush();
        return toDomain(entity);
    }

    @Override
    public Optional<StoredFile> findFileById(UUID fileId) {
        return files.findById(fileId).map(this::toDomain);
    }

    @Override
    public Optional<StoredFile> findActiveInputUploadIntent(UUID caseId, Instant now) {
        return files.findActiveUploadIntents(caseId, FileKind.INPUT, now,
                org.springframework.data.domain.PageRequest.of(0, 1)).stream().findFirst().map(this::toDomain);
    }

    @Override
    public Optional<StoredFile> findFileByIdForUpdate(UUID fileId) {
        return files.findByIdForUpdate(fileId).map(this::toDomain);
    }

    @Override
    public long countFilesByCaseIdAndKind(UUID caseId, FileKind fileKind) {
        return files.countByMedicalCaseIdAndFileKindAndInputActiveTrueAndConfirmedAtIsNotNull(caseId, fileKind);
    }

    private MedicalCase toDomain(MedicalCaseEntity entity) {
        return new MedicalCase(
                entity.getId(),
                entity.getPatient().getId(),
                entity.getReferenceCode(),
                entity.getStudyName(),
                entity.getAdditionalReference(), entity.getOriginInstitution(), entity.getStudyDate(), entity.getNotes(),
                entity.getStatus(),
                entity.getCreatedAt(),
                entity.getUpdatedAt(), entity.getReceivedAt());
    }

    private CaseConsent toDomain(CaseConsentEntity entity) {
        return new CaseConsent(
                entity.getId(),
                entity.getMedicalCase().getId(),
                entity.getPatient().getId(),
                entity.getTermsVersion(),
                entity.getAcceptedAt());
    }

    private StoredFile toDomain(StoredFileEntity entity) {
        StoredFile file = new StoredFile(
                entity.getId(),
                entity.getMedicalCase().getId(),
                entity.getObjectKey(),
                entity.getFileKind(),
                entity.getOriginalFilename(),
                entity.getContentType(),
                entity.getSizeBytes(),
                entity.getCreatedAt(),
                entity.getUpdatedAt(),
                entity.getUploadInitiatedBy(),
                entity.getUploadExpiresAt(), entity.isInputActive());
        if (entity.getConfirmedAt() != null) file.restoreConfirmation(entity.getConfirmedAt());
        return file;
    }
}
