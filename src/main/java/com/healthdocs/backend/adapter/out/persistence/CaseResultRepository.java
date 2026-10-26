package com.healthdocs.backend.adapter.out.persistence;

import com.healthdocs.backend.adapter.out.persistence.entity.CaseResultEntity;
import java.util.UUID;
import java.util.Optional;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.repository.query.Param;
import com.healthdocs.backend.domain.model.ResultStatus;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CaseResultRepository extends JpaRepository<CaseResultEntity, UUID> {
    @org.springframework.data.jpa.repository.Query(value = "select r from CaseResultEntity r join fetch r.file f join fetch r.medicalCase c where c.patient.id = :patientId and c.status = com.healthdocs.backend.domain.model.CaseStatus.RESULT_AVAILABLE and r.current = true and r.status = com.healthdocs.backend.domain.model.ResultStatus.AVAILABLE and r.deletedAt is null and r.versionNumber is not null and r.publicationType is not null and f.fileKind = com.healthdocs.backend.domain.model.FileKind.RESULT and (:publicationType is null or r.publicationType = :publicationType) and (:q = '' or lower(c.referenceCode) like concat('%', :q, '%') or lower(c.studyName) like concat('%', :q, '%') or lower(f.originalFilename) like concat('%', :q, '%'))",
            countQuery = "select count(r) from CaseResultEntity r join r.file f join r.medicalCase c where c.patient.id = :patientId and c.status = com.healthdocs.backend.domain.model.CaseStatus.RESULT_AVAILABLE and r.current = true and r.status = com.healthdocs.backend.domain.model.ResultStatus.AVAILABLE and r.deletedAt is null and r.versionNumber is not null and r.publicationType is not null and f.fileKind = com.healthdocs.backend.domain.model.FileKind.RESULT and (:publicationType is null or r.publicationType = :publicationType) and (:q = '' or lower(c.referenceCode) like concat('%', :q, '%') or lower(c.studyName) like concat('%', :q, '%') or lower(f.originalFilename) like concat('%', :q, '%'))")
    Page<CaseResultEntity> findPublishedPatientResults(@Param("patientId") UUID patientId,
            @Param("publicationType") com.healthdocs.backend.domain.model.ResultPublicationType publicationType,
            @Param("q") String q, Pageable pageable);

    @org.springframework.data.jpa.repository.Query("select count(r) from CaseResultEntity r join r.file f join r.medicalCase c where c.patient.id = :patientId and c.status = com.healthdocs.backend.domain.model.CaseStatus.RESULT_AVAILABLE and r.current = true and r.status = com.healthdocs.backend.domain.model.ResultStatus.AVAILABLE and r.deletedAt is null and r.versionNumber is not null and r.publicationType is not null and f.fileKind = com.healthdocs.backend.domain.model.FileKind.RESULT")
    long countPublishedPatientResults(@Param("patientId") UUID patientId);

    @org.springframework.data.jpa.repository.Query("select max(r.availableAt) from CaseResultEntity r join r.file f join r.medicalCase c where c.patient.id = :patientId and c.status = com.healthdocs.backend.domain.model.CaseStatus.RESULT_AVAILABLE and r.current = true and r.status = com.healthdocs.backend.domain.model.ResultStatus.AVAILABLE and r.deletedAt is null and r.versionNumber is not null and r.publicationType is not null and f.fileKind = com.healthdocs.backend.domain.model.FileKind.RESULT")
    java.time.Instant latestPublishedPatientResult(@Param("patientId") UUID patientId);
    @org.springframework.data.jpa.repository.Query(value = "select r from CaseResultEntity r join fetch r.file f join fetch r.medicalCase c where (r.current = true or r.status = com.healthdocs.backend.domain.model.ResultStatus.UPLOADED) and r.deletedAt is null and (:coordinator = true or exists (select a from CaseAssignmentEntity a where a.caseId = c.id and a.userId = :userId)) and (:status is null or r.status = :status) and (:q = '' or lower(f.originalFilename) like concat('%', :q, '%') or lower(c.referenceCode) like concat('%', :q, '%') or lower(c.studyName) like concat('%', :q, '%'))",
            countQuery = "select count(r) from CaseResultEntity r join r.file f join r.medicalCase c where (r.current = true or r.status = com.healthdocs.backend.domain.model.ResultStatus.UPLOADED) and r.deletedAt is null and (:coordinator = true or exists (select a from CaseAssignmentEntity a where a.caseId = c.id and a.userId = :userId)) and (:status is null or r.status = :status) and (:q = '' or lower(f.originalFilename) like concat('%', :q, '%') or lower(c.referenceCode) like concat('%', :q, '%') or lower(c.studyName) like concat('%', :q, '%'))")
    Page<CaseResultEntity> findVisibleResults(@Param("userId") UUID userId, @Param("coordinator") boolean coordinator,
            @Param("status") ResultStatus status, @Param("q") String q, Pageable pageable);

    @org.springframework.data.jpa.repository.Query("select r.status, count(r) from CaseResultEntity r join r.file f join r.medicalCase c where (r.current = true or r.status = com.healthdocs.backend.domain.model.ResultStatus.UPLOADED) and r.deletedAt is null and (:coordinator = true or exists (select a from CaseAssignmentEntity a where a.caseId = c.id and a.userId = :userId)) and (:status is null or r.status = :status) and (:q = '' or lower(f.originalFilename) like concat('%', :q, '%') or lower(c.referenceCode) like concat('%', :q, '%') or lower(c.studyName) like concat('%', :q, '%')) group by r.status")
    List<Object[]> summarizeVisibleResults(@Param("userId") UUID userId, @Param("coordinator") boolean coordinator,
            @Param("status") ResultStatus status, @Param("q") String q);

    @org.springframework.data.jpa.repository.Query(value = "select r from CaseResultEntity r join fetch r.file f join fetch r.medicalCase c where c.id = :caseId",
            countQuery = "select count(r) from CaseResultEntity r where r.medicalCase.id = :caseId")
    Page<CaseResultEntity> findHistory(@Param("caseId") UUID caseId, Pageable pageable);

    @org.springframework.data.jpa.repository.Query("select r from CaseResultEntity r join fetch r.file f join fetch r.medicalCase c where c.id = :caseId and r.deletedAt is null and (r.current = true or r.status = com.healthdocs.backend.domain.model.ResultStatus.UPLOADED) order by r.createdAt desc")
    List<CaseResultEntity> findAcademicDetailResults(@Param("caseId") UUID caseId);

    boolean existsByMedicalCaseId(UUID caseId);

    Optional<CaseResultEntity> findByMedicalCaseIdAndCurrentTrueAndDeletedAtIsNull(UUID caseId);

    Optional<CaseResultEntity> findFirstByMedicalCaseIdAndDeletedAtIsNullOrderByCurrentDescCreatedAtDesc(UUID caseId);

    /** Compatibility lookup; prefers the vigente row and otherwise the latest pending row. */
    default Optional<CaseResultEntity> findByMedicalCaseId(UUID caseId) {
        return findFirstByMedicalCaseIdAndDeletedAtIsNullOrderByCurrentDescCreatedAtDesc(caseId);
    }

    boolean existsByMedicalCaseIdAndStatusAndDeletedAtIsNull(UUID caseId, com.healthdocs.backend.domain.model.ResultStatus status);

    long countByMedicalCaseIdAndStatusAndDeletedAtIsNull(UUID caseId, com.healthdocs.backend.domain.model.ResultStatus status);

    @org.springframework.data.jpa.repository.Query("select coalesce(max(r.versionNumber), 0) from CaseResultEntity r where r.medicalCase.id = :caseId")
    int findMaximumVersionNumber(@org.springframework.data.repository.query.Param("caseId") UUID caseId);
}
