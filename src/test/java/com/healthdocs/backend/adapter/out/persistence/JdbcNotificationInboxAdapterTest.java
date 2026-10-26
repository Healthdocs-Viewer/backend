package com.healthdocs.backend.adapter.out.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

class JdbcNotificationInboxAdapterTest {
    private JdbcTemplate jdbc;
    private JdbcNotificationInboxAdapter adapter;

    @BeforeEach
    void setUp() {
        var dataSource = new DriverManagerDataSource(
                "jdbc:h2:mem:notification-inbox-query;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE", "sa", "");
        jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("DROP ALL OBJECTS");
        jdbc.execute("CREATE TABLE medical_case (id UUID PRIMARY KEY, patient_id UUID NOT NULL, reference_code VARCHAR(50) NOT NULL)");
        jdbc.update("INSERT INTO medical_case(id, patient_id, reference_code) VALUES (?, ?, ?)",
                uuid(99), uuid(10), "CAS-0001");
        jdbc.execute("CREATE TABLE notification_inbox (id UUID PRIMARY KEY, recipient_user_id UUID, notification_type VARCHAR(50), case_id UUID, related_entity_type VARCHAR(30), related_entity_id UUID, created_at TIMESTAMP WITH TIME ZONE, read_at TIMESTAMP WITH TIME ZONE)");
        adapter = new JdbcNotificationInboxAdapter(jdbc);
    }

    @Test
    void queryAndReadRequireRecipientToOwnTheCase() {
        UUID patient = uuid(10);
        Instant time = Instant.parse("2026-10-24T12:00:00Z");
        insert(uuid(1), patient, time);
        insert(uuid(2), patient, time);
        insert(uuid(3), uuid(11), time.plusSeconds(10));

        var page = adapter.findByRecipient(patient, 1, 1);

        assertEquals(2, page.totalElements());
        assertEquals(1, page.events().size());
        assertEquals(uuid(1), page.events().get(0).id());
        assertEquals("CAS-0001", page.events().get(0).caseReferenceCode());

        var otherPatientPage = adapter.findByRecipient(uuid(11), 20, 0);
        assertEquals(0, otherPatientPage.totalElements());
        assertTrue(adapter.markRead(uuid(3), uuid(11), time.plusSeconds(20)).isEmpty());
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM notification_inbox WHERE id = ? AND read_at IS NOT NULL",
                Integer.class, uuid(3)));
    }

    @Test
    void markingReadIsIdempotentAndPreservesFirstReadTime() {
        Instant created = Instant.parse("2026-10-24T12:00:00Z");
        Instant firstRead = created.plusSeconds(10);
        insert(uuid(4), uuid(10), created);

        var first = adapter.markRead(uuid(4), uuid(10), firstRead).orElseThrow();
        var repeated = adapter.markRead(uuid(4), uuid(10), firstRead.plusSeconds(30)).orElseThrow();

        assertEquals(firstRead, first.readAt());
        assertEquals(firstRead, repeated.readAt());
    }

    private void insert(UUID id, UUID recipient, Instant createdAt) {
        jdbc.update("INSERT INTO notification_inbox(id,recipient_user_id,notification_type,case_id,related_entity_type,related_entity_id,created_at) VALUES(?,?,'RESULT_AVAILABLE',?,'RESULT',?,?)",
                id, recipient, uuid(99), uuid(100), createdAt);
    }

    private static UUID uuid(long n) { return new UUID(0, n); }
}
