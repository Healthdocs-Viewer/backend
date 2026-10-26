package com.healthdocs.backend.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.healthdocs.backend.application.port.out.ActivityEventPort;
import com.healthdocs.backend.application.port.out.AuthProvider.AuthenticatedUser;
import com.healthdocs.backend.domain.model.Role;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

class RetentionLifecycleServiceTest {
    private static final Instant NOW = Instant.parse("2026-10-24T12:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    @Test
    void patientDeletionRequestPersistsAndRecordsActivityInSameServiceTransaction() throws Exception {
        UUID patientId = UUID.randomUUID();
        UUID caseId = UUID.randomUUID();
        UUID requestId = UUID.randomUUID();
        var request = request(requestId, caseId, "REQUESTED", false);
        var jdbc = new FakeJdbcTemplate(patientId, request, false);
        var activity = new RecordingActivity();
        var service = new RetentionLifecycleService(jdbc, activity, CLOCK);

        var result = service.requestCaseDeletion(patient(patientId), caseId, UUID.randomUUID());

        assertEquals(requestId, result.id());
        assertEquals("REQUESTED", result.status());
        assertTrue(!result.replayed());
        assertEquals(1, jdbc.updates);
        assertEquals(1, activity.events.size());
        assertEquals("CASE_DELETION_REQUESTED", activity.events.getFirst().type());
        assertTrue(RetentionLifecycleService.class.getMethod("requestCaseDeletion", AuthenticatedUser.class,
                UUID.class, UUID.class).isAnnotationPresent(org.springframework.transaction.annotation.Transactional.class));
    }

    @Test
    void repeatedIdempotencyKeyReturnsOriginalRequestWithoutWritingOrEmittingAgain() {
        UUID patientId = UUID.randomUUID();
        UUID caseId = UUID.randomUUID();
        UUID requestId = UUID.randomUUID();
        var existing = request(requestId, caseId, "REQUESTED", false);
        var jdbc = new FakeJdbcTemplate(patientId, existing, true);
        var activity = new RecordingActivity();
        var result = new RetentionLifecycleService(jdbc, activity, CLOCK)
                .requestCaseDeletion(patient(patientId), caseId, UUID.randomUUID());

        assertEquals(requestId, result.id());
        assertTrue(result.replayed());
        assertEquals(0, jdbc.updates);
        assertTrue(activity.events.isEmpty());
    }

    @Test
    void rejectsNonPatientAndDisabledAccountBeforeAccessingPersistence() {
        var jdbc = new FakeJdbcTemplate(null, null, false);
        var service = new RetentionLifecycleService(jdbc, new RecordingActivity(), CLOCK);
        assertThrows(ForbiddenOperationException.class, () -> service.requestCaseDeletion(
                new AuthenticatedUser(UUID.randomUUID(), "ignored", Role.ADMIN, true), UUID.randomUUID(), UUID.randomUUID()));
        assertThrows(ForbiddenOperationException.class, () -> service.requestCaseDeletion(
                new AuthenticatedUser(UUID.randomUUID(), "ignored", Role.PATIENT, false), UUID.randomUUID(), UUID.randomUUID()));
        assertEquals(0, jdbc.queries);
    }

    private static AuthenticatedUser patient(UUID id) {
        return new AuthenticatedUser(id, "ignored", Role.PATIENT, true);
    }

    private static RetentionLifecycleService.DeletionRequest request(UUID id, UUID caseId, String status,
            boolean replayed) {
        return new RetentionLifecycleService.DeletionRequest(id, caseId, null, status, NOW, null, null,
                null, "CASE", replayed);
    }

    private static final class RecordingActivity implements ActivityEventPort {
        private final List<Event> events = new ArrayList<>();
        @Override
        public void record(String type, Instant occurredAt, UUID caseId, UUID actorUserId, UUID subjectUserId,
                String relatedEntityType, UUID relatedEntityId, String deduplicationKey) {
            events.add(new Event(type, caseId, actorUserId, deduplicationKey));
        }
    }

    private record Event(String type, UUID caseId, UUID actorId, String deduplicationKey) { }

    private static final class FakeJdbcTemplate extends JdbcTemplate {
        private final UUID owner;
        private final RetentionLifecycleService.DeletionRequest existing;
        private final boolean replay;
        private int queries;
        private int updates;

        private FakeJdbcTemplate(UUID owner, RetentionLifecycleService.DeletionRequest existing, boolean replay) {
            this.owner = owner;
            this.existing = existing;
            this.replay = replay;
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T> List<T> query(String sql, RowMapper<T> rowMapper, Object... args) {
            queries++;
            if (sql.contains("FROM medical_case")) return (List<T>) List.of(owner);
            if (sql.contains("idempotency_key")) return replay ? (List<T>) List.of(existing) : List.of();
            if (sql.contains("FROM case_deletion_request")) return (List<T>) List.of(existing);
            throw new AssertionError("Unexpected query: " + sql);
        }

        @Override
        public <T> T queryForObject(String sql, Class<T> requiredType, Object... args) {
            if (requiredType == Boolean.class) return requiredType.cast(false);
            throw new AssertionError("Unexpected scalar query: " + sql);
        }

        @Override
        public int update(String sql, Object... args) {
            updates++;
            return 1;
        }
    }
}
