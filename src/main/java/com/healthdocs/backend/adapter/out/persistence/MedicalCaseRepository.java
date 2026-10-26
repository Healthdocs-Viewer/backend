package com.healthdocs.backend.adapter.out.persistence;

import com.healthdocs.backend.adapter.out.persistence.entity.MedicalCaseEntity;
import java.time.Instant;
import java.util.UUID;
import java.util.List;
import com.healthdocs.backend.domain.model.CaseStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.query.Param;

public interface MedicalCaseRepository extends JpaRepository<MedicalCaseEntity, UUID> {

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select c from MedicalCaseEntity c where c.id = :caseId")
    java.util.Optional<MedicalCaseEntity> findByIdForUpdate(UUID caseId);

    boolean existsByIdAndPatientId(UUID caseId, UUID patientId);

    List<MedicalCaseEntity> findAllByPatientIdOrderByCreatedAtDesc(UUID patientId);

    long countByCreatedAtGreaterThanEqualAndCreatedAtLessThan(Instant startInclusive, Instant endExclusive);

    @org.springframework.data.jpa.repository.Query(value = "SELECT COALESCE(MAX(CAST(SUBSTRING(reference_code, 14) AS BIGINT)), 0) FROM medical_case WHERE reference_code LIKE CONCAT(:prefix, '%')", nativeQuery = true)
    long maxReferenceNumberWithPrefix(@Param("prefix") String prefix);

    @org.springframework.data.jpa.repository.Query("select distinct c from MedicalCaseEntity c join StoredFileEntity f on f.medicalCase = c join CaseAssignmentEntity a on a.caseId = c.id where a.userId = :userId and c.status in :statuses and f.fileKind = com.healthdocs.backend.domain.model.FileKind.INPUT and f.inputActive = true order by c.createdAt desc")
    List<MedicalCaseEntity> findAssignedInternalCases(UUID userId, List<CaseStatus> statuses);

    // Compatibilidad con pruebas y consumidores existentes que solo necesitan
    // casos habilitados sin resultado asociado.
    @org.springframework.data.jpa.repository.Query("select distinct c from MedicalCaseEntity c join StoredFileEntity f on f.medicalCase = c join CaseAssignmentEntity a on a.caseId = c.id where a.userId = :userId and c.status = :status and f.fileKind = com.healthdocs.backend.domain.model.FileKind.INPUT and f.inputActive = true and not exists (select r from CaseResultEntity r where r.medicalCase = c)")
    List<MedicalCaseEntity> findEnabledForUser(UUID userId, CaseStatus status);

    @org.springframework.data.jpa.repository.Query("select distinct c from MedicalCaseEntity c join StoredFileEntity f on f.medicalCase = c where c.status = :status and f.fileKind = com.healthdocs.backend.domain.model.FileKind.INPUT and f.inputActive = true and not exists (select r from CaseResultEntity r where r.medicalCase = c) order by c.createdAt desc")
    List<MedicalCaseEntity> findPendingCases(CaseStatus status);

    @org.springframework.data.jpa.repository.Query("select distinct c from MedicalCaseEntity c join StoredFileEntity f on f.medicalCase = c where c.status in :statuses and f.fileKind = com.healthdocs.backend.domain.model.FileKind.INPUT and f.inputActive = true order by c.createdAt desc")
    List<MedicalCaseEntity> findAcademicCases(List<CaseStatus> statuses);
}
