package com.healthdocs.backend.adapter.out.persistence;

import com.healthdocs.backend.adapter.out.persistence.entity.StoredFileEntity;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StoredFileRepository extends JpaRepository<StoredFileEntity, UUID> {

    boolean existsByIdAndMedicalCaseId(UUID fileId, UUID caseId);
}
