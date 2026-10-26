package com.healthdocs.backend.adapter.out.persistence;

import com.healthdocs.backend.application.port.out.TechnicalOperationEventPort;
import java.sql.Timestamp;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class TechnicalOperationEventRecorder implements TechnicalOperationEventPort {
    private final JdbcTemplate jdbc;

    public TechnicalOperationEventRecorder(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void record(Event event) {
        jdbc.update("""
                INSERT INTO technical_operation_event (
                    id, event_type, operation_type, operation_id, correlation_id, case_id, resource_id,
                    attempt_number, error_code, occurred_at, deduplication_key, schema_version
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 1)
                ON CONFLICT (deduplication_key) DO NOTHING
                """, UUID.randomUUID(), event.eventType(), event.operationType(), event.operationId(),
                event.correlationId(), event.caseId(), event.resourceId(), event.attemptNumber(), event.errorCode(),
                Timestamp.from(event.occurredAt()), event.deduplicationKey());
    }
}
