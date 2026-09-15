package com.healthdocs.backend.adapter.out.persistence;

import com.healthdocs.backend.adapter.out.persistence.entity.CaseAssignmentEntity;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CaseAssignmentRepository
        extends JpaRepository<CaseAssignmentEntity, CaseAssignmentEntity.AssignmentId> {

    boolean existsByCaseIdAndUserId(UUID caseId, UUID userId);
}
