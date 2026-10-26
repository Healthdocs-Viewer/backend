package com.healthdocs.backend.adapter.out.persistence;

import com.healthdocs.backend.adapter.out.persistence.entity.InputChangeRequestFileEntity;
import com.healthdocs.backend.domain.model.InputChangeFileSet;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InputChangeRequestFileRepository extends JpaRepository<InputChangeRequestFileEntity, UUID> {

    List<InputChangeRequestFileEntity> findAllByRequest_IdAndFileSetAndRemovedAtIsNullOrderByAddedAtAsc(
            UUID requestId, InputChangeFileSet fileSet);

    Optional<InputChangeRequestFileEntity> findFirstByRequest_IdAndFile_IdAndFileSetAndRemovedAtIsNull(
            UUID requestId, UUID fileId, InputChangeFileSet fileSet);

    Optional<InputChangeRequestFileEntity> findFirstByFile_IdAndFileSetAndRemovedAtIsNullOrderByAddedAtDesc(
            UUID fileId, InputChangeFileSet fileSet);
}
