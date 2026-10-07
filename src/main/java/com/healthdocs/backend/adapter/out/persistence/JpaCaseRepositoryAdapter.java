package com.healthdocs.backend.adapter.out.persistence;

import com.healthdocs.backend.adapter.out.persistence.entity.AppUserEntity;
import com.healthdocs.backend.adapter.out.persistence.entity.CaseConsentEntity;
import com.healthdocs.backend.adapter.out.persistence.entity.MedicalCaseEntity;
import com.healthdocs.backend.adapter.out.persistence.entity.StoredFileEntity;
import com.healthdocs.backend.application.port.out.CaseRepositoryPort;
import com.healthdocs.backend.application.service.ResourceNotFoundException;
import com.healthdocs.backend.domain.model.CaseConsent;
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

    @PersistenceContext
    private EntityManager entityManager;

    public JpaCaseRepositoryAdapter(
            MedicalCaseRepository cases,
            StoredFileRepository files,
            CaseConsentRepository consents,
            AppUserRepository users) {
        this.cases = cases;
        this.files = files;
        this.consents = consents;
        this.users = users;
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
        entity.setStatus(medicalCase.status());
        entity.setUpdatedAt(medicalCase.updatedAt());
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
    public List<MedicalCase> findAllByPatientId(UUID patientId) {
        return cases.findAllByPatientIdOrderByCreatedAtDesc(patientId).stream()
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
                        file.sizeBytes()));
        entity.setUpdatedAt(file.updatedAt());
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
    public long countFilesByCaseIdAndKind(UUID caseId, FileKind fileKind) {
        return files.countByMedicalCaseIdAndFileKind(caseId, fileKind);
    }

    private MedicalCase toDomain(MedicalCaseEntity entity) {
        return new MedicalCase(
                entity.getId(),
                entity.getPatient().getId(),
                entity.getReferenceCode(),
                entity.getStudyName(),
                entity.getStatus(),
                entity.getCreatedAt(),
                entity.getUpdatedAt());
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
        return new StoredFile(
                entity.getId(),
                entity.getMedicalCase().getId(),
                entity.getObjectKey(),
                entity.getFileKind(),
                entity.getOriginalFilename(),
                entity.getContentType(),
                entity.getSizeBytes(),
                entity.getCreatedAt(),
                entity.getUpdatedAt());
    }
}
