package com.healthdocs.backend.adapter.out.persistence;

import com.healthdocs.backend.application.port.out.NotificationOutboxPort;
import java.sql.Timestamp;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class NotificationOutboxRecorder implements NotificationOutboxPort {
    private final JdbcTemplate jdbc;

    public NotificationOutboxRecorder(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void enqueue(String eventType, String sourceEventKey, UUID caseId, UUID recipientUserId,
            UUID publishedByUserId, String relatedEntityType, UUID relatedEntityId,
            java.time.Instant occurredAt) {
        jdbc.update("""
                INSERT INTO notification_outbox (
                    id, event_type, source_event_key, case_id, recipient_user_id,
                    published_by_user_id, related_entity_type, related_entity_id,
                    occurred_at, status, attempt_count, next_attempt_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, 'PENDING', 0, CURRENT_TIMESTAMP)
                ON CONFLICT (source_event_key) DO NOTHING
                """, UUID.randomUUID(), eventType, sourceEventKey, caseId, recipientUserId,
                publishedByUserId, relatedEntityType, relatedEntityId, Timestamp.from(occurredAt));
    }
}
