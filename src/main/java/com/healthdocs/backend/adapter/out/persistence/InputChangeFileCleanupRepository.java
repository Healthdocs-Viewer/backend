package com.healthdocs.backend.adapter.out.persistence;

import com.healthdocs.backend.adapter.out.persistence.entity.InputChangeFileCleanupEntity;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface InputChangeFileCleanupRepository extends JpaRepository<InputChangeFileCleanupEntity, UUID> {
    @Query(value = """
            SELECT * FROM input_change_file_cleanup
            WHERE processed_at IS NULL AND eligible_at <= :now
            ORDER BY eligible_at, file_id
            LIMIT 50
            FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    List<InputChangeFileCleanupEntity> findDueForUpdate(@Param("now") Instant now);
}
