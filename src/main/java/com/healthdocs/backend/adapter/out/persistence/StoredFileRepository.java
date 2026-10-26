package com.healthdocs.backend.adapter.out.persistence;

import com.healthdocs.backend.adapter.out.persistence.entity.StoredFileEntity;
import com.healthdocs.backend.domain.model.FileKind;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StoredFileRepository extends JpaRepository<StoredFileEntity, UUID> {

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select f from StoredFileEntity f where f.id = :fileId")
    java.util.Optional<StoredFileEntity> findByIdForUpdate(UUID fileId);

    boolean existsByIdAndMedicalCaseId(UUID fileId, UUID caseId);

    java.util.Optional<StoredFileEntity> findByObjectKey(String objectKey);

    long countByMedicalCaseIdAndFileKind(UUID caseId, FileKind fileKind);

    long countByMedicalCaseIdAndFileKindAndInputActiveTrue(UUID caseId, FileKind fileKind);

    long countByMedicalCaseIdAndFileKindAndConfirmedAtIsNotNull(UUID caseId, FileKind fileKind);

    long countByMedicalCaseIdAndFileKindAndInputActiveTrueAndConfirmedAtIsNotNull(UUID caseId, FileKind fileKind);

    java.util.List<StoredFileEntity> findByMedicalCaseId(UUID caseId);

    java.util.List<StoredFileEntity> findByMedicalCaseIdAndFileKindAndInputActiveTrue(UUID caseId, FileKind fileKind);

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select f from StoredFileEntity f where f.medicalCase.id = :caseId and f.fileKind = :fileKind and f.confirmedAt is null and f.uploadExpiresAt > :now order by f.createdAt asc")
    java.util.List<StoredFileEntity> findActiveUploadIntents(
            @org.springframework.data.repository.query.Param("caseId") UUID caseId,
            @org.springframework.data.repository.query.Param("fileKind") FileKind fileKind,
            @org.springframework.data.repository.query.Param("now") java.time.Instant now,
            org.springframework.data.domain.Pageable pageable);
}
