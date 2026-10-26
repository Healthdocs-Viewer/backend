package com.healthdocs.backend.adapter.out.persistence;

import com.healthdocs.backend.application.port.out.NotificationInboxPort;
import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcNotificationInboxAdapter implements NotificationInboxPort {
    private final JdbcTemplate jdbc;

    public JdbcNotificationInboxAdapter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Page findByRecipient(UUID recipientUserId, int limit, long offset) {
        Long total = jdbc.queryForObject(
                """
                SELECT COUNT(*)
                FROM notification_inbox n
                JOIN medical_case c ON c.id = n.case_id AND c.patient_id = n.recipient_user_id
                WHERE n.recipient_user_id = ?
                """, Long.class, recipientUserId);
        List<Event> events = jdbc.query("""
                SELECT n.id, n.notification_type, c.reference_code AS case_reference_code,
                       n.created_at, n.read_at
                FROM notification_inbox n
                JOIN medical_case c ON c.id = n.case_id AND c.patient_id = n.recipient_user_id
                WHERE n.recipient_user_id = ?
                ORDER BY n.created_at DESC, n.id DESC
                LIMIT ? OFFSET ?
                """, (rs, rowNum) -> new Event(rs.getObject("id", UUID.class),
                rs.getString("notification_type"), rs.getString("case_reference_code"),
                rs.getTimestamp("created_at").toInstant(),
                rs.getTimestamp("read_at") == null ? null : rs.getTimestamp("read_at").toInstant()),
                recipientUserId, limit, offset);
        return new Page(events, total == null ? 0 : total);
    }

    @Override
    public Optional<Event> markRead(UUID notificationId, UUID recipientUserId, java.time.Instant readAt) {
        int updated = jdbc.update("""
                UPDATE notification_inbox
                SET read_at = COALESCE(read_at, ?)
                WHERE id = ? AND recipient_user_id = ?
                  AND EXISTS (SELECT 1 FROM medical_case c
                      WHERE c.id = notification_inbox.case_id
                        AND c.patient_id = notification_inbox.recipient_user_id)
                """, Timestamp.from(readAt), notificationId, recipientUserId);
        if (updated != 1) return Optional.empty();
        List<Event> result = jdbc.query("""
                SELECT n.id, n.notification_type, c.reference_code AS case_reference_code,
                       n.created_at, n.read_at
                FROM notification_inbox n
                JOIN medical_case c ON c.id = n.case_id AND c.patient_id = n.recipient_user_id
                WHERE n.id = ? AND n.recipient_user_id = ?
                """, (rs, rowNum) -> new Event(rs.getObject("id", UUID.class),
                rs.getString("notification_type"), rs.getString("case_reference_code"),
                rs.getTimestamp("created_at").toInstant(),
                rs.getTimestamp("read_at").toInstant()), notificationId, recipientUserId);
        return result.stream().findFirst();
    }

    @Override
    public boolean emailEnabled(UUID userId, String notificationType) {
        List<Boolean> values = jdbc.query("""
                SELECT enabled FROM notification_preference
                WHERE user_id = ? AND notification_type = ? AND channel = 'EMAIL'
                """, (rs, rowNum) -> rs.getBoolean(1), userId, notificationType);
        return !values.isEmpty() && values.get(0);
    }

    @Override
    public void setEmailEnabled(UUID userId, String notificationType, boolean enabled, java.time.Instant updatedAt) {
        jdbc.update("""
                INSERT INTO notification_preference (id, user_id, notification_type, channel, enabled, updated_at)
                VALUES (?, ?, ?, 'EMAIL', ?, ?)
                ON CONFLICT (user_id, notification_type, channel)
                DO UPDATE SET enabled = EXCLUDED.enabled, updated_at = EXCLUDED.updated_at
                """, UUID.randomUUID(), userId, notificationType, enabled, Timestamp.from(updatedAt));
        if (!enabled) {
            jdbc.update("""
                    UPDATE notification_channel_delivery d
                    SET status = 'SKIPPED', lease_until = NULL
                    FROM notification_inbox n
                    WHERE d.notification_id = n.id AND n.recipient_user_id = ?
                      AND n.notification_type = ? AND d.channel = 'EMAIL' AND d.status = 'PENDING'
                    """, userId, notificationType);
        }
    }
}
