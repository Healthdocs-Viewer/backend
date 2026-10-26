package com.healthdocs.backend.adapter.out.persistence;

import com.healthdocs.backend.application.port.out.SecurityAuditEventPort;
import java.sql.PreparedStatement;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.Instant;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class SecurityAuditEventRecorder implements SecurityAuditEventPort {
    private final JdbcTemplate jdbc;

    public SecurityAuditEventRecorder(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void record(String type, Instant occurredAt, UUID actorUserId, UUID subjectUserId, UUID caseId,
            String resourceType, UUID resourceId, String action, String changedField,
            String previousValue, String newValue, String deduplicationKey) {
        jdbc.update(connection -> {
            PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO security_audit_event (
                        id, type, occurred_at, actor_user_id, subject_user_id, case_id,
                        resource_type, resource_id, action, changed_field, previous_value,
                        new_value, deduplication_key, schema_version
                    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 1)
                    ON CONFLICT (deduplication_key) DO NOTHING
                    """);
            statement.setObject(1, UUID.randomUUID());
            statement.setString(2, type);
            statement.setTimestamp(3, Timestamp.from(occurredAt));
            setUuid(statement, 4, actorUserId);
            setUuid(statement, 5, subjectUserId);
            setUuid(statement, 6, caseId);
            setString(statement, 7, resourceType);
            setUuid(statement, 8, resourceId);
            setString(statement, 9, action);
            setString(statement, 10, changedField);
            setString(statement, 11, previousValue);
            setString(statement, 12, newValue);
            statement.setString(13, deduplicationKey);
            return statement;
        });
    }

    private static void setUuid(PreparedStatement statement, int index, UUID value) throws java.sql.SQLException {
        if (value == null) statement.setNull(index, Types.OTHER);
        else statement.setObject(index, value);
    }

    private static void setString(PreparedStatement statement, int index, String value) throws java.sql.SQLException {
        if (value == null) statement.setNull(index, Types.VARCHAR);
        else statement.setString(index, value);
    }
}
