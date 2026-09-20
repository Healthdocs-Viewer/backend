package com.healthdocs.backend.adapter.out.persistence;

import com.healthdocs.backend.adapter.out.persistence.entity.CaseResultEntity;
import java.util.UUID;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CaseResultRepository extends JpaRepository<CaseResultEntity, UUID> {
    boolean existsByMedicalCaseId(UUID caseId);
    Optional<CaseResultEntity> findByMedicalCaseId(UUID caseId);
}
