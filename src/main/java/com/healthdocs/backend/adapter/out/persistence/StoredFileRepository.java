package com.healthdocs.backend.adapter.out.persistence;

import com.healthdocs.backend.adapter.out.persistence.entity.StoredFileEntity;
import com.healthdocs.backend.domain.model.FileKind;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StoredFileRepository extends JpaRepository<StoredFileEntity, UUID> {

    boolean existsByIdAndMedicalCaseId(UUID fileId, UUID caseId);

    long countByMedicalCaseIdAndFileKind(UUID caseId, FileKind fileKind);

    java.util.List<StoredFileEntity> findByMedicalCaseId(UUID caseId);
}
