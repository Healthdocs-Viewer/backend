package com.healthdocs.backend.adapter.out.persistence;

import com.healthdocs.backend.adapter.out.persistence.entity.CaseReferenceCounterEntity;
import java.time.LocalDate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface CaseReferenceCounterRepository extends JpaRepository<CaseReferenceCounterEntity, LocalDate> {
    @Query(value = "SELECT lock_id FROM case_reference_lock WHERE lock_id = 1 FOR UPDATE", nativeQuery = true)
    Integer lockSequence();
}
