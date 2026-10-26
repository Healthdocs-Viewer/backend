package com.healthdocs.backend.application.service;

import com.healthdocs.backend.application.port.out.ResultNotificationPublisher;
import com.healthdocs.backend.application.port.out.TechnicalOperationEventPort;
import com.healthdocs.backend.adapter.out.storage.SqsNotificationException;
import com.healthdocs.backend.domain.model.ResultAvailableEvent;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.context.annotation.Profile;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** Materializes durable inbox entries and dispatches opted-in email deliveries. */
@Component
@Profile("!test")
public class NotificationOutboxWorker {
    private static final Logger log = LoggerFactory.getLogger(NotificationOutboxWorker.class);
    private static final int BATCH_SIZE = 50;
    private static final int MAX_OUTBOX_ATTEMPTS = 5;
    private static final int MAX_EMAIL_ATTEMPTS = 5;
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transactions;
    private final ResultNotificationPublisher publisher;
    private final TechnicalOperationEventPort technicalEvents;
    private final Clock clock;

    public NotificationOutboxWorker(JdbcTemplate jdbc, PlatformTransactionManager transactionManager,
            ResultNotificationPublisher publisher, TechnicalOperationEventPort technicalEvents, Clock clock) {
        this.jdbc = jdbc;
        this.transactions = new TransactionTemplate(transactionManager);
        this.publisher = publisher;
        this.technicalEvents = technicalEvents;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${healthdocs.notifications.worker-delay-ms:5000}")
    public void run() {
        materializeOutbox();
        dispatchEmailBatch();
    }

    public int materializeOutbox() {
        List<OutboxRow> rows = transactions.execute(status -> claimOutboxBatch());
        if (rows == null || rows.isEmpty()) return 0;
        for (OutboxRow row : rows) {
            try {
                transactions.executeWithoutResult(status -> materialize(row));
            } catch (RuntimeException failure) {
                recordOutboxFailure(row);
            }
        }
        return rows.size();
    }

    private List<OutboxRow> claimOutboxBatch() {
        return jdbc.query("""
                WITH due AS (
                    SELECT id
                    FROM notification_outbox
                    WHERE (status = 'PENDING' AND next_attempt_at <= CURRENT_TIMESTAMP)
                       OR (status = 'PROCESSING' AND lease_until < CURRENT_TIMESTAMP)
                    ORDER BY created_at, id
                    LIMIT ?
                    FOR UPDATE SKIP LOCKED
                ), claimed AS (
                    UPDATE notification_outbox o
                    SET status = 'PROCESSING', attempt_count = o.attempt_count + 1,
                        lease_until = CURRENT_TIMESTAMP + INTERVAL '2 minutes'
                    FROM due
                    WHERE o.id = due.id
                    RETURNING o.id, o.attempt_count
                )
                SELECT o.id, o.event_type, o.source_event_key, o.case_id, o.recipient_user_id,
                       o.related_entity_type, o.related_entity_id, o.occurred_at, claimed.attempt_count
                FROM claimed JOIN notification_outbox o ON o.id = claimed.id
                ORDER BY o.created_at, o.id
                """, (rs, rowNum) -> new OutboxRow(rs.getObject("id", UUID.class),
                rs.getString("event_type"), rs.getString("source_event_key"),
                rs.getObject("case_id", UUID.class), rs.getObject("recipient_user_id", UUID.class),
                rs.getString("related_entity_type"), rs.getObject("related_entity_id", UUID.class),
                rs.getTimestamp("occurred_at").toInstant(), rs.getInt("attempt_count")), BATCH_SIZE);
    }

    private void recordOutboxFailure(OutboxRow row) {
        try {
            transactions.executeWithoutResult(status -> {
                boolean exhausted = row.attemptCount >= MAX_OUTBOX_ATTEMPTS;
                Instant now = clock.instant();
                int updated = jdbc.update("""
                        UPDATE notification_outbox
                        SET status = ?, next_attempt_at = ?, lease_until = NULL,
                            failed_at = ?, last_error_code = ?
                        WHERE id = ? AND status = 'PROCESSING' AND attempt_count = ?
                        """, exhausted ? "FAILED" : "PENDING",
                        Timestamp.from(now.plus(backoff(row.attemptCount))), exhausted ? Timestamp.from(now) : null,
                        exhausted ? "OUTBOX_PROCESSING_ERROR" : null, row.id, row.attemptCount);
                if (updated != 1) return;
                technicalEvents.record(new TechnicalOperationEventPort.Event(
                        "EVENT_PUBLISH_FAILED", "OUTBOX_MATERIALIZATION", row.id, row.id,
                        row.caseId, row.relatedEntityId, row.attemptCount, "OUTBOX_PROCESSING_ERROR", now,
                        "EVENT_PUBLISH_FAILED:" + row.id + ":" + row.attemptCount));
            });
        } catch (RuntimeException telemetryFailure) {
            log.warn("Outbox failure state could not be persisted for operation {} attempt {}",
                    row.id, row.attemptCount);
        }
    }

    public int dispatchEmailBatch() {
        List<EmailDelivery> deliveries = transactions.execute(status -> jdbc.query("""
                WITH due AS (
                    SELECT d.id
                    FROM notification_channel_delivery d
                    WHERE d.channel = 'EMAIL'
                      AND ((d.status = 'PENDING' AND d.next_attempt_at <= CURRENT_TIMESTAMP)
                           OR (d.status = 'PROCESSING' AND d.lease_until < CURRENT_TIMESTAMP))
                    ORDER BY d.next_attempt_at, d.queued_at, d.id
                    LIMIT ?
                    FOR UPDATE SKIP LOCKED
                ), claimed AS (
                    UPDATE notification_channel_delivery d
                    SET status = 'PROCESSING', attempt_count = d.attempt_count + 1,
                        lease_until = CURRENT_TIMESTAMP + INTERVAL '2 minutes',
                        last_attempt_at = CURRENT_TIMESTAMP
                    FROM due
                    WHERE d.id = due.id
                    RETURNING d.id, d.notification_id, d.attempt_count
                )
                SELECT claimed.id AS delivery_id, claimed.attempt_count,
                       n.id AS notification_id, n.case_id, n.related_entity_id AS result_id,
                       n.recipient_user_id, o.published_by_user_id, o.occurred_at,
                       u.email, u.enabled,
                       COALESCE((SELECT p.enabled FROM notification_preference p
                           WHERE p.user_id = n.recipient_user_id
                             AND p.notification_type = 'RESULT_AVAILABLE' AND p.channel = 'EMAIL'), FALSE)
                           AS preference_enabled
                FROM claimed
                JOIN notification_inbox n ON n.id = claimed.notification_id
                LEFT JOIN notification_outbox o ON o.source_event_key = n.source_event_key
                LEFT JOIN app_user u ON u.id = n.recipient_user_id
                """, (rs, rowNum) -> new EmailDelivery(rs.getObject("delivery_id", UUID.class),
                rs.getInt("attempt_count"), rs.getObject("notification_id", UUID.class),
                rs.getObject("case_id", UUID.class), rs.getObject("result_id", UUID.class),
                rs.getObject("recipient_user_id", UUID.class), rs.getObject("published_by_user_id", UUID.class),
                rs.getTimestamp("occurred_at") == null ? null : rs.getTimestamp("occurred_at").toInstant(),
                rs.getString("email"), rs.getObject("enabled") == null ? null : rs.getBoolean("enabled"),
                rs.getBoolean("preference_enabled")), BATCH_SIZE));
        if (deliveries == null) return 0;
        for (EmailDelivery delivery : deliveries) dispatch(delivery);
        return deliveries.size();
    }

    private void materialize(OutboxRow row) {
        List<UUID> currentAttempt = jdbc.query("""
                SELECT id FROM notification_outbox
                WHERE id = ? AND status = 'PROCESSING' AND attempt_count = ?
                FOR UPDATE
                """, (rs, rowNum) -> rs.getObject("id", UUID.class), row.id, row.attemptCount);
        if (currentAttempt.isEmpty()) return;
        if (row.recipientUserId == null) {
            completeOutbox(row.id, row.attemptCount);
            return;
        }
        UUID notificationId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO notification_inbox (
                    id, recipient_user_id, notification_type, case_id, related_entity_type,
                    related_entity_id, source_event_key, created_at, schema_version
                ) VALUES (?, ?, 'RESULT_AVAILABLE', ?, ?, ?, ?, ?, 1)
                """, notificationId, row.recipientUserId, row.caseId, row.relatedEntityType,
                row.relatedEntityId, row.sourceEventKey, Timestamp.from(row.occurredAt));
        UUID persistedId = jdbc.queryForObject("""
                SELECT id FROM notification_inbox
                WHERE source_event_key = ? AND recipient_user_id = ?
                """, UUID.class, row.sourceEventKey, row.recipientUserId);
        Instant now = clock.instant();
        jdbc.update("""
                INSERT INTO notification_channel_delivery (
                    id, notification_id, channel, status, attempt_count, queued_at,
                    next_attempt_at, delivered_at
                ) VALUES (?, ?, 'IN_APP', 'DELIVERED', 0, ?, ?, ?)
                """, UUID.randomUUID(), persistedId, Timestamp.from(now), Timestamp.from(now), Timestamp.from(now));
        boolean emailEnabled = Boolean.TRUE.equals(jdbc.queryForObject("""
                SELECT COALESCE((SELECT enabled FROM notification_preference
                    WHERE user_id = ? AND notification_type = 'RESULT_AVAILABLE' AND channel = 'EMAIL'), FALSE)
                """, Boolean.class, row.recipientUserId));
        jdbc.update("""
                INSERT INTO notification_channel_delivery (
                    id, notification_id, channel, status, attempt_count, queued_at, next_attempt_at
                ) VALUES (?, ?, 'EMAIL', ?, 0, ?, ?)
                """, UUID.randomUUID(), persistedId, emailEnabled ? "PENDING" : "SKIPPED",
                Timestamp.from(now), Timestamp.from(now));
        completeOutbox(row.id, row.attemptCount);
    }

    private void completeOutbox(UUID id, int attemptCount) {
        jdbc.update("""
                UPDATE notification_outbox
                SET status = 'COMPLETED', processed_at = CURRENT_TIMESTAMP, lease_until = NULL,
                    failed_at = NULL, last_error_code = NULL
                WHERE id = ? AND status = 'PROCESSING' AND attempt_count = ?
                """, id, attemptCount);
    }

    private void dispatch(EmailDelivery delivery) {
        if (delivery.recipientUserId == null || delivery.email == null || !Boolean.TRUE.equals(delivery.enabled)
                || !delivery.preferenceEnabled || !publisher.isDeliveryEnabled()
                || delivery.occurredAt == null) {
            jdbc.update("""
                    UPDATE notification_channel_delivery
                    SET status = 'SKIPPED', lease_until = NULL
                            WHERE id = ? AND status = 'PROCESSING' AND attempt_count = ?
                            """, delivery.deliveryId, delivery.attemptCount);
            return;
        }
        try {
            publisher.publish(new ResultAvailableEvent(ResultAvailableEvent.CURRENT_VERSION,
                    ResultAvailableEvent.EVENT_TYPE, delivery.notificationId, delivery.resultId,
                    delivery.caseId, delivery.recipientUserId, delivery.email, delivery.occurredAt,
                    clock.instant(), delivery.publishedByUserId));
        } catch (RuntimeException failure) {
            boolean retryable = isRetryableEmailFailure(failure);
            boolean exhausted = delivery.attemptCount >= MAX_EMAIL_ATTEMPTS;
            boolean retry = shouldRetryEmailFailure(failure, delivery.attemptCount);
            String errorCode = deliveryErrorCode(failure);
            try {
                transactions.executeWithoutResult(status -> {
                    Instant now = clock.instant();
                    int updated = jdbc.update("""
                            UPDATE notification_channel_delivery
                            SET status = ?, next_attempt_at = ?, lease_until = NULL,
                                failed_at = CASE WHEN ? THEN CURRENT_TIMESTAMP ELSE NULL END
                            WHERE id = ? AND status = 'PROCESSING' AND attempt_count = ?
                            """, retry ? "PENDING" : "FAILED",
                            Timestamp.from(now.plus(backoff(delivery.attemptCount))), !retry, delivery.deliveryId,
                            delivery.attemptCount);
                    if (updated != 1) return;
                    technicalEvents.record(new TechnicalOperationEventPort.Event(
                            "NOTIFICATION_DELIVERY_FAILED", "NOTIFICATION_EMAIL_DELIVERY", delivery.deliveryId,
                            delivery.notificationId, delivery.caseId, delivery.resultId, delivery.attemptCount,
                            errorCode, now, "NOTIFICATION_DELIVERY_FAILED:" + delivery.deliveryId + ":"
                                    + delivery.attemptCount));
                    if (retryable && exhausted) {
                        technicalEvents.record(new TechnicalOperationEventPort.Event(
                                "NOTIFICATION_RETRY_EXHAUSTED", "NOTIFICATION_EMAIL_DELIVERY",
                                delivery.deliveryId, delivery.notificationId, delivery.caseId, delivery.resultId,
                                delivery.attemptCount, errorCode, now,
                                "NOTIFICATION_RETRY_EXHAUSTED:" + delivery.deliveryId + ":"
                                        + delivery.attemptCount));
                    }
                });
                log.warn("Notification delivery failed for operation {} attempt {} code {}",
                        delivery.deliveryId, delivery.attemptCount, errorCode);
            } catch (RuntimeException telemetryFailure) {
                log.warn("Notification failure state could not be persisted for operation {} attempt {}",
                        delivery.deliveryId, delivery.attemptCount);
            }
            return;
        }

        transactions.executeWithoutResult(status -> {
            Instant now = clock.instant();
            int updated = jdbc.update("""
                    UPDATE notification_channel_delivery
                    SET status = 'SENT', sent_at = CURRENT_TIMESTAMP, lease_until = NULL
                    WHERE id = ? AND status = 'PROCESSING' AND attempt_count = ?
                    """, delivery.deliveryId, delivery.attemptCount);
            if (updated != 1) return;
            technicalEvents.record(new TechnicalOperationEventPort.Event(
                    "NOTIFICATION_DELIVERY_ACCEPTED", "NOTIFICATION_EMAIL_DELIVERY", delivery.deliveryId,
                    delivery.notificationId, delivery.caseId, delivery.resultId, delivery.attemptCount,
                    null, now, "NOTIFICATION_DELIVERY_ACCEPTED:" + delivery.deliveryId + ":"
                            + delivery.attemptCount));
        });
    }

    static String deliveryErrorCode(RuntimeException failure) {
        if (failure instanceof SqsNotificationException sqs) {
            return switch (sqs.kind()) {
                case TIMEOUT -> "DELIVERY_TIMEOUT";
                case TEMPORARY -> "DELIVERY_TEMPORARY_ERROR";
                case PERMANENT, CONFIGURATION, SERIALIZATION -> "DELIVERY_PERMANENT_ERROR";
            };
        }
        return "DELIVERY_UNKNOWN_ERROR";
    }

    static boolean isRetryableEmailFailure(RuntimeException failure) {
        return !(failure instanceof SqsNotificationException sqs)
                || sqs.kind() == SqsNotificationException.Kind.TEMPORARY
                || sqs.kind() == SqsNotificationException.Kind.TIMEOUT;
    }

    static boolean shouldRetryEmailFailure(RuntimeException failure, int attemptCount) {
        return isRetryableEmailFailure(failure) && attemptCount < MAX_EMAIL_ATTEMPTS;
    }

    static Duration backoff(int attempt) {
        long seconds = Math.min(3600, 30L * (1L << Math.min(7, Math.max(0, attempt - 1))));
        return Duration.ofSeconds(seconds);
    }

    private record OutboxRow(UUID id, String eventType, String sourceEventKey, UUID caseId,
            UUID recipientUserId, String relatedEntityType, UUID relatedEntityId, Instant occurredAt,
            int attemptCount) { }
    private record EmailDelivery(UUID deliveryId, int attemptCount, UUID notificationId, UUID caseId,
            UUID resultId, UUID recipientUserId, UUID publishedByUserId, Instant occurredAt,
            String email, Boolean enabled, boolean preferenceEnabled) { }
}
