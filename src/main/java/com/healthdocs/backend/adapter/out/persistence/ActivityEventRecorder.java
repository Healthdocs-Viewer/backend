package com.healthdocs.backend.adapter.out.persistence;

import java.sql.PreparedStatement;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.Instant;
import java.util.UUID;
import com.healthdocs.backend.application.port.out.ActivityEventPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** Persists business activity in the caller's transaction; it does not publish notifications. */
@Repository
public class ActivityEventRecorder implements ActivityEventPort {
    private final JdbcTemplate jdbc;

    public ActivityEventRecorder(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void record(String type, Instant occurredAt, UUID caseId, UUID actorUserId,
            UUID subjectUserId, String relatedEntityType, UUID relatedEntityId, String deduplicationKey) {
        jdbc.update(connection -> {
            PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO activity_event (
                        id, type, occurred_at, case_id, actor_user_id, subject_user_id,
                        related_entity_type, related_entity_id, deduplication_key, schema_version
                    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, 1)
                    ON CONFLICT (deduplication_key) DO NOTHING
                    """);
            statement.setObject(1, UUID.randomUUID());
            statement.setString(2, type);
            statement.setTimestamp(3, Timestamp.from(occurredAt));
            setUuid(statement, 4, caseId);
            setUuid(statement, 5, actorUserId);
            setUuid(statement, 6, subjectUserId);
            if (relatedEntityType == null) statement.setNull(7, Types.VARCHAR);
            else statement.setString(7, relatedEntityType);
            setUuid(statement, 8, relatedEntityId);
            statement.setString(9, deduplicationKey);
            return statement;
        });
    }

    private static void setUuid(PreparedStatement statement, int index, UUID value) throws java.sql.SQLException {
        if (value == null) statement.setNull(index, Types.OTHER);
        else statement.setObject(index, value);
    }
}
