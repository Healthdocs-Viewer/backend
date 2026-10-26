package com.healthdocs.backend.adapter.out.persistence;

import com.healthdocs.backend.adapter.out.persistence.entity.InputChangeRequestEntity;
import com.healthdocs.backend.domain.model.InputChangeRequestStatus;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InputChangeRequestRepository extends JpaRepository<InputChangeRequestEntity, UUID> {

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select r from InputChangeRequestEntity r where r.id = :requestId")
    Optional<InputChangeRequestEntity> findByIdForUpdate(UUID requestId);

    @org.springframework.data.jpa.repository.Query("select r.medicalCase.id from InputChangeRequestEntity r where r.id = :requestId")
    Optional<UUID> findCaseIdById(UUID requestId);

    Optional<InputChangeRequestEntity> findByIdAndRequestedBy_Id(UUID id, UUID ownerId);

    List<InputChangeRequestEntity> findAllByRequestedBy_IdOrderByCreatedAtDesc(UUID ownerId);

    List<InputChangeRequestEntity> findAllByMedicalCase_IdAndRequestedBy_IdOrderByCreatedAtDesc(
            UUID caseId, UUID ownerId);

    List<InputChangeRequestEntity> findAllByMedicalCase_IdOrderByCreatedAtAsc(UUID caseId);

    Optional<InputChangeRequestEntity> findFirstByMedicalCase_IdAndStatusIn(
            UUID caseId, Collection<InputChangeRequestStatus> activeStatuses);

    Optional<InputChangeRequestEntity> findFirstByMedicalCase_IdAndRequestedBy_IdAndStatusIn(
            UUID caseId, UUID ownerId, Collection<InputChangeRequestStatus> activeStatuses);

    boolean existsByMedicalCase_IdAndStatusIn(
            UUID caseId, Collection<InputChangeRequestStatus> activeStatuses);

    List<InputChangeRequestEntity> findAllByStatusOrderBySubmittedAtAsc(InputChangeRequestStatus status);
}
