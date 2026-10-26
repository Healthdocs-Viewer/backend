package com.healthdocs.backend.adapter.out.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.time.Instant;
import java.time.Clock;
import java.time.ZoneOffset;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;
import com.healthdocs.backend.application.port.out.ResultNotificationPublisher;
import com.healthdocs.backend.application.service.NotificationOutboxWorker;

class NotificationPersistenceIdempotencyPostgresTest {
    private static PostgreSQLContainer<?> postgres;
    private static JdbcTemplate jdbc;
    private static UUID patientId;
    private static UUID otherPatientId;
    private static UUID coordinatorId;
    private static UUID caseId;
    private static UUID resultId;

    @BeforeEach
    void migratePostgresAndSeedNotificationOwners() {
        boolean dockerAvailable;
        try {
            dockerAvailable = DockerClientFactory.instance().isDockerAvailable();
        } catch (RuntimeException unavailable) {
            dockerAvailable = false;
        }
        assumeTrue(dockerAvailable, "PostgreSQL integration requires an available Docker daemon");

        postgres = new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"));
        postgres.start();
        Flyway.configure()
                .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                .locations("classpath:db/migration")
                .load()
                .migrate();
        jdbc = new JdbcTemplate(new DriverManagerDataSource(
                postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword()));

        patientId = UUID.randomUUID();
        otherPatientId = UUID.randomUUID();
        coordinatorId = UUID.randomUUID();
        caseId = UUID.randomUUID();
        resultId = UUID.randomUUID();
        insertUser(patientId, "patient-owner");
        insertUser(otherPatientId, "other-patient");
        insertUser(coordinatorId, "coordinator");
        jdbc.update("""
                INSERT INTO medical_case(id, patient_id, status, reference_code, study_name)
                VALUES (?, ?, 'RESULT_AVAILABLE', ?, 'TEST_STUDY')
                """, caseId, patientId, "CAS-" + caseId.toString().substring(0, 8));
    }

    @AfterEach
    void stopPostgres() {
        if (postgres != null) postgres.stop();
    }

    @Test
    void outboxInboxAndChannelUniquenessAreEnforcedByMigratedPostgresSchema() {
        Instant occurredAt = Instant.parse("2026-10-24T12:00:00Z");
        String eventKey = "RESULT_PUBLISHED:" + resultId + ":1";
        var recorder = new NotificationOutboxRecorder(jdbc);

        recorder.enqueue("RESULT_PUBLISHED", eventKey, caseId, patientId, coordinatorId,
                "RESULT", resultId, occurredAt);
        recorder.enqueue("RESULT_PUBLISHED", eventKey, caseId, otherPatientId, coordinatorId,
                "RESULT", resultId, occurredAt.plusSeconds(1));

        assertThrows(DataIntegrityViolationException.class,
                () -> recorder.enqueue("INPUT_CHANGE_OPENED", "INPUT_CHANGE_OPENED:request-1", caseId,
                        patientId, coordinatorId, "RESULT", resultId, occurredAt));

        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM notification_outbox", Integer.class));
        assertEquals(patientId, jdbc.queryForObject(
                "SELECT recipient_user_id FROM notification_outbox WHERE source_event_key = ?",
                UUID.class, eventKey));
        assertEquals(occurredAt, jdbc.queryForObject(
                "SELECT occurred_at FROM notification_outbox WHERE source_event_key = ?",
                java.sql.Timestamp.class, eventKey).toInstant());

        UUID notificationId = UUID.randomUUID();
        insertInbox(notificationId, patientId, eventKey, occurredAt);
        assertThrows(DuplicateKeyException.class,
                () -> insertInbox(UUID.randomUUID(), patientId, eventKey, occurredAt));

        insertDelivery(notificationId, "IN_APP", "DELIVERED", occurredAt);
        insertDelivery(notificationId, "EMAIL", "PENDING", occurredAt);
        assertThrows(DuplicateKeyException.class,
                () -> insertDelivery(UUID.randomUUID(), notificationId, "IN_APP", "DELIVERED", occurredAt));
        assertEquals(2, jdbc.queryForObject(
                "SELECT COUNT(*) FROM notification_channel_delivery WHERE notification_id = ?",
                Integer.class, notificationId));
    }

    @Test
    void schemaAtV32UpgradesThroughV33AndV34() {
        Flyway legacy = flywayForSchema("notification_upgrade", MigrationVersion.fromVersion("32"));
        legacy.migrate();
        assertEquals("32", legacy.info().current().getVersion().getVersion());

        Flyway upgraded = flywayForSchema("notification_upgrade", null);
        upgraded.migrate();

        assertEquals("34", upgraded.info().current().getVersion().getVersion());
        assertEquals(1, jdbc.queryForObject("""
                SELECT COUNT(*) FROM information_schema.columns
                WHERE table_schema = 'notification_upgrade'
                  AND table_name = 'notification_outbox' AND column_name = 'failed_at'
                """, Integer.class));
        assertEquals(1, jdbc.queryForObject("""
                SELECT COUNT(*) FROM information_schema.columns
                WHERE table_schema = 'notification_upgrade'
                  AND table_name = 'notification_channel_delivery' AND column_name = 'failed_at'
                """, Integer.class));
        assertEquals(1, jdbc.queryForObject("""
                SELECT COUNT(*) FROM information_schema.tables
                WHERE table_schema = 'notification_upgrade' AND table_name = 'technical_operation_event'
                """, Integer.class));
    }

    @Test
    void materializationFailureRollsBackInboxAndEndsOutboxAtFiveAttempts() {
        Instant occurredAt = Instant.now().minusSeconds(60);
        String eventKey = "worker-failure:" + UUID.randomUUID();
        new NotificationOutboxRecorder(jdbc).enqueue("RESULT_PUBLISHED", eventKey, caseId, patientId,
                coordinatorId, "RESULT", resultId, occurredAt);
        jdbc.execute("""
                CREATE FUNCTION reject_email_delivery() RETURNS trigger LANGUAGE plpgsql AS $$
                BEGIN
                    IF NEW.channel = 'EMAIL' THEN RAISE EXCEPTION 'injected failure'; END IF;
                    RETURN NEW;
                END $$
                """);
        jdbc.execute("""
                CREATE TRIGGER reject_email_delivery_trigger BEFORE INSERT ON notification_channel_delivery
                FOR EACH ROW EXECUTE FUNCTION reject_email_delivery()
                """);
        var worker = new NotificationOutboxWorker(jdbc,
                new DataSourceTransactionManager(jdbc.getDataSource()),
                new ResultNotificationPublisher() {
                    @Override public void publish(com.healthdocs.backend.domain.model.ResultAvailableEvent event) { }
                    @Override public boolean isDeliveryEnabled() { return true; }
                }, new TechnicalOperationEventRecorder(jdbc),
                Clock.fixed(Instant.parse("2026-10-24T12:00:00Z"), ZoneOffset.UTC));

        for (int attempt = 1; attempt <= 5; attempt++) {
            worker.materializeOutbox();
            assertEquals(0, jdbc.queryForObject("""
                    SELECT COUNT(*) FROM notification_inbox WHERE source_event_key = ?
                    """, Integer.class, eventKey));
            assertEquals(0, jdbc.queryForObject("""
                    SELECT COUNT(*) FROM notification_channel_delivery d
                    JOIN notification_inbox n ON n.id = d.notification_id WHERE n.source_event_key = ?
                    """, Integer.class, eventKey));
            if (attempt < 5) {
                jdbc.update("UPDATE notification_outbox SET next_attempt_at = CURRENT_TIMESTAMP - INTERVAL '1 second' WHERE source_event_key = ?", eventKey);
            }
        }
        assertEquals("FAILED", jdbc.queryForObject(
                "SELECT status FROM notification_outbox WHERE source_event_key = ?", String.class, eventKey));
        assertEquals(5, jdbc.queryForObject(
                "SELECT attempt_count FROM notification_outbox WHERE source_event_key = ?", Integer.class, eventKey));
        assertEquals("OUTBOX_PROCESSING_ERROR", jdbc.queryForObject(
                "SELECT last_error_code FROM notification_outbox WHERE source_event_key = ?", String.class, eventKey));
        assertEquals(5, jdbc.queryForObject("""
                SELECT COUNT(*) FROM technical_operation_event
                WHERE operation_id = (SELECT id FROM notification_outbox WHERE source_event_key = ?)
                """, Integer.class, eventKey));
    }

    @Test
    void emailTemporaryFailuresRetryAndBecomeTerminalAfterFiveAttempts() {
        Instant occurredAt = Instant.now().minusSeconds(60);
        String eventKey = "email-retry:" + UUID.randomUUID();
        new NotificationOutboxRecorder(jdbc).enqueue("RESULT_PUBLISHED", eventKey, caseId, patientId,
                coordinatorId, "RESULT", resultId, occurredAt);
        UUID notificationId = UUID.randomUUID();
        insertInbox(notificationId, patientId, eventKey, occurredAt);
        insertDelivery(notificationId, "EMAIL", "PENDING", occurredAt);
        jdbc.update("""
                INSERT INTO notification_preference(id, user_id, notification_type, channel, enabled, updated_at)
                VALUES (?, ?, 'RESULT_AVAILABLE', 'EMAIL', TRUE, CURRENT_TIMESTAMP)
                """, UUID.randomUUID(), patientId);
        var worker = new NotificationOutboxWorker(jdbc,
                new DataSourceTransactionManager(jdbc.getDataSource()), event -> {
                    throw new com.healthdocs.backend.adapter.out.storage.SqsNotificationException(
                            com.healthdocs.backend.adapter.out.storage.SqsNotificationException.Kind.TEMPORARY,
                            "provider message with private details");
                }, new TechnicalOperationEventRecorder(jdbc),
                Clock.fixed(Instant.parse("2026-10-24T12:00:00Z"), ZoneOffset.UTC));

        for (int attempt = 1; attempt <= 5; attempt++) {
            worker.dispatchEmailBatch();
            assertEquals(attempt, jdbc.queryForObject("""
                    SELECT attempt_count FROM notification_channel_delivery
                    WHERE notification_id = ? AND channel = 'EMAIL'
                    """, Integer.class, notificationId));
            if (attempt < 5) {
                assertEquals("PENDING", jdbc.queryForObject("""
                        SELECT status FROM notification_channel_delivery
                        WHERE notification_id = ? AND channel = 'EMAIL'
                        """, String.class, notificationId));
                jdbc.update("""
                        UPDATE notification_channel_delivery SET next_attempt_at = CURRENT_TIMESTAMP - INTERVAL '1 second'
                        WHERE notification_id = ? AND channel = 'EMAIL'
                        """, notificationId);
            }
        }
        assertEquals("FAILED", jdbc.queryForObject("""
                SELECT status FROM notification_channel_delivery
                WHERE notification_id = ? AND channel = 'EMAIL'
                """, String.class, notificationId));
        assertEquals(1, jdbc.queryForObject("""
                SELECT COUNT(*) FROM technical_operation_event
                WHERE event_type = 'NOTIFICATION_RETRY_EXHAUSTED' AND operation_id =
                  (SELECT id FROM notification_channel_delivery WHERE notification_id = ? AND channel = 'EMAIL')
                """, Integer.class, notificationId));
    }

    @Test
    void concurrentWorkersDoNotDispatchTheSameLeasedEmailTwice() throws Exception {
        Instant occurredAt = Instant.now().minusSeconds(60);
        String eventKey = "email-concurrency:" + UUID.randomUUID();
        new NotificationOutboxRecorder(jdbc).enqueue("RESULT_PUBLISHED", eventKey, caseId, patientId,
                coordinatorId, "RESULT", resultId, occurredAt);
        UUID notificationId = UUID.randomUUID();
        insertInbox(notificationId, patientId, eventKey, occurredAt);
        insertDelivery(notificationId, "EMAIL", "PENDING", occurredAt);
        jdbc.update("""
                INSERT INTO notification_preference(id, user_id, notification_type, channel, enabled, updated_at)
                VALUES (?, ?, 'RESULT_AVAILABLE', 'EMAIL', TRUE, CURRENT_TIMESTAMP)
                """, UUID.randomUUID(), patientId);
        var enteredSender = new CountDownLatch(1);
        var releaseSender = new CountDownLatch(1);
        ResultNotificationPublisher blockingPublisher = event -> {
            enteredSender.countDown();
            try {
                if (!releaseSender.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("sender wait timed out");
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(interrupted);
            }
        };
        var transactionManager = new DataSourceTransactionManager(jdbc.getDataSource());
        var firstWorker = new NotificationOutboxWorker(jdbc, transactionManager, blockingPublisher,
                new TechnicalOperationEventRecorder(jdbc), Clock.systemUTC());
        var secondWorker = new NotificationOutboxWorker(jdbc, transactionManager, blockingPublisher,
                new TechnicalOperationEventRecorder(jdbc), Clock.systemUTC());
        var executor = Executors.newSingleThreadExecutor();
        try {
            var firstRun = executor.submit(firstWorker::dispatchEmailBatch);
            org.junit.jupiter.api.Assertions.assertTrue(enteredSender.await(5, TimeUnit.SECONDS));
            assertEquals(0, secondWorker.dispatchEmailBatch());
            releaseSender.countDown();
            assertEquals(1, firstRun.get(5, TimeUnit.SECONDS));
        } finally {
            releaseSender.countDown();
            executor.shutdownNow();
        }
        assertEquals("SENT", jdbc.queryForObject("""
                SELECT status FROM notification_channel_delivery
                WHERE notification_id = ? AND channel = 'EMAIL'
                """, String.class, notificationId));
        assertEquals(1, jdbc.queryForObject("""
                SELECT attempt_count FROM notification_channel_delivery
                WHERE notification_id = ? AND channel = 'EMAIL'
                """, Integer.class, notificationId));
    }

    private Flyway flywayForSchema(String schema, MigrationVersion target) {
        var configuration = Flyway.configure()
                .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                .locations("classpath:db/migration")
                .schemas(schema)
                .defaultSchema(schema)
                .createSchemas(true);
        if (target != null) configuration.target(target);
        return configuration.load();
    }

    private static void insertUser(UUID id, String subject) {
        jdbc.update("INSERT INTO app_user(id, external_subject, email, role) VALUES (?, ?, ?, ?)",
                id, subject, subject + "@test.example", subject.equals("coordinator") ? "coordinator" : "patient");
    }

    private static void insertInbox(UUID id, UUID recipientId, String eventKey, Instant createdAt) {
        jdbc.update("""
                INSERT INTO notification_inbox(
                    id, recipient_user_id, notification_type, case_id, related_entity_type,
                    related_entity_id, source_event_key, created_at, schema_version
                ) VALUES (?, ?, 'RESULT_AVAILABLE', ?, 'RESULT', ?, ?, ?, 1)
                """, id, recipientId, caseId, resultId, eventKey, java.sql.Timestamp.from(createdAt));
    }

    private static void insertDelivery(UUID notificationId, String channel, String status, Instant queuedAt) {
        insertDelivery(UUID.randomUUID(), notificationId, channel, status, queuedAt);
    }

    private static void insertDelivery(UUID id, UUID notificationId, String channel, String status,
            Instant queuedAt) {
        jdbc.update("""
                INSERT INTO notification_channel_delivery(
                    id, notification_id, channel, status, attempt_count, queued_at
                ) VALUES (?, ?, ?, ?, 0, ?)
                """, id, notificationId, channel, status, java.sql.Timestamp.from(queuedAt));
    }
}
