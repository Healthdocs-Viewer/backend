package com.healthdocs.backend.adapter.out.persistence;

import com.healthdocs.backend.adapter.out.persistence.entity.CaseCreationIdempotencyEntity;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CaseCreationIdempotencyRepository extends JpaRepository<CaseCreationIdempotencyEntity, UUID> {
    Optional<CaseCreationIdempotencyEntity> findByPatient_IdAndIdempotencyKey(UUID patientId, UUID idempotencyKey);
}
