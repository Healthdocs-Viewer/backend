package com.healthdocs.backend.adapter.out.persistence;

import com.healthdocs.backend.adapter.out.persistence.entity.CaseResultEntity;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CaseResultRepository extends JpaRepository<CaseResultEntity, UUID> {
    boolean existsByMedicalCaseId(UUID caseId);
}
