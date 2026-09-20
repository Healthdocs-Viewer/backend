package com.healthdocs.backend.adapter.out.persistence;

import com.healthdocs.backend.adapter.out.persistence.entity.MedicalCaseEntity;
import java.time.Instant;
import java.util.UUID;
import java.util.List;
import com.healthdocs.backend.domain.model.CaseStatus;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MedicalCaseRepository extends JpaRepository<MedicalCaseEntity, UUID> {

    boolean existsByIdAndPatientId(UUID caseId, UUID patientId);

    List<MedicalCaseEntity> findAllByPatientIdOrderByCreatedAtDesc(UUID patientId);

    long countByCreatedAtGreaterThanEqualAndCreatedAtLessThan(Instant startInclusive, Instant endExclusive);

    @org.springframework.data.jpa.repository.Query("select distinct c from MedicalCaseEntity c join StoredFileEntity f on f.medicalCase = c join CaseAssignmentEntity a on a.caseId = c.id where a.userId = :userId and c.status in :statuses and f.fileKind = com.healthdocs.backend.domain.model.FileKind.INPUT order by c.createdAt desc")
    List<MedicalCaseEntity> findAssignedInternalCases(UUID userId, List<CaseStatus> statuses);

    // Compatibilidad con pruebas y consumidores existentes que solo necesitan
    // casos habilitados sin resultado asociado.
    @org.springframework.data.jpa.repository.Query("select distinct c from MedicalCaseEntity c join StoredFileEntity f on f.medicalCase = c join CaseAssignmentEntity a on a.caseId = c.id where a.userId = :userId and c.status = :status and f.fileKind = com.healthdocs.backend.domain.model.FileKind.INPUT and not exists (select r from CaseResultEntity r where r.medicalCase = c)")
    List<MedicalCaseEntity> findEnabledForUser(UUID userId, CaseStatus status);

    @org.springframework.data.jpa.repository.Query("select distinct c from MedicalCaseEntity c join StoredFileEntity f on f.medicalCase = c where c.status = :status and f.fileKind = com.healthdocs.backend.domain.model.FileKind.INPUT and not exists (select r from CaseResultEntity r where r.medicalCase = c) order by c.createdAt desc")
    List<MedicalCaseEntity> findPendingCases(CaseStatus status);
}
