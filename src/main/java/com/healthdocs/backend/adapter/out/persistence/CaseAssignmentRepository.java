package com.healthdocs.backend.adapter.out.persistence;

import com.healthdocs.backend.adapter.out.persistence.entity.CaseAssignmentEntity;
import java.util.UUID;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CaseAssignmentRepository
        extends JpaRepository<CaseAssignmentEntity, CaseAssignmentEntity.AssignmentId> {

    boolean existsByCaseIdAndUserId(UUID caseId, UUID userId);

    List<CaseAssignmentEntity> findAllByCaseId(UUID caseId);

    List<CaseAssignmentEntity> findAllByUserId(UUID userId);

    void deleteByCaseIdAndUserId(UUID caseId, UUID userId);
}
