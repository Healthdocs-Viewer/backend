package com.healthdocs.backend.adapter.out.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.healthdocs.backend.application.port.out.ActivityEventQueryPort;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

class JdbcActivityEventQueryAdapterTest {
    private final UUID caseId = UUID.fromString("10000000-0000-0000-0000-000000000000");
    private final UUID patientId = UUID.fromString("20000000-0000-0000-0000-000000000000");
    private JdbcTemplate jdbc;
    private JdbcActivityEventQueryAdapter adapter;

    @BeforeEach
    void setUp() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                "jdbc:h2:mem:activity-query;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE", "sa", "");
        jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("DROP ALL OBJECTS");
        jdbc.execute("CREATE TABLE medical_case (id UUID PRIMARY KEY, patient_id UUID NOT NULL, reference_code VARCHAR(32) NOT NULL)");
        jdbc.execute("CREATE TABLE activity_event (id UUID PRIMARY KEY, type VARCHAR(50), occurred_at TIMESTAMP WITH TIME ZONE, case_id UUID, related_entity_id UUID)");
        jdbc.execute("CREATE TABLE case_result (id UUID PRIMARY KEY, case_id UUID, status VARCHAR(50), deleted_at TIMESTAMP WITH TIME ZONE)");
        adapter = new JdbcActivityEventQueryAdapter(jdbc);
    }

    @Test
    void patientFilterExcludesUnpublishedResultEventsAndUsesStableDescendingOrder() {
        UUID tiedId1 = uuid(1);
        UUID tiedId2 = uuid(2);
        UUID publishedResultId = uuid(3);
        UUID pendingResultId = uuid(4);
        Instant sameTime = Instant.parse("2026-10-24T12:00:00Z");
        insert(tiedId1, "CASE_CREATED", sameTime, null);
        insert(tiedId2, "CASE_CREATED", sameTime, null);
        insert(publishedResultId, "RESULT_PUBLISHED", sameTime.plusSeconds(1), publishedResultId);
        insert(pendingResultId, "RESULT_PUBLISHED", sameTime.plusSeconds(2), pendingResultId);
        jdbc.update("INSERT INTO case_result(id, case_id, status) VALUES (?, ?, 'AVAILABLE')", publishedResultId, caseId);
        jdbc.update("INSERT INTO case_result(id, case_id, status) VALUES (?, ?, 'UPLOADED')", pendingResultId, caseId);

        var page = adapter.findByCase(caseId, Set.of("CASE_CREATED", "RESULT_PUBLISHED"), 10, 0);

        assertEquals(3, page.size());
        assertEquals(publishedResultId, page.get(0).id());
        assertEquals(tiedId2, page.get(1).id());
        assertEquals(tiedId1, page.get(2).id());
        assertEquals(3, adapter.countByCase(caseId, Set.of("CASE_CREATED", "RESULT_PUBLISHED")));
    }

    @Test
    void appliesLimitAndOffsetAfterCaseFilterAndSort() {
        Instant base = Instant.parse("2026-10-24T12:00:00Z");
        insert(uuid(1), "CASE_CREATED", base, null);
        insert(uuid(2), "CONSENT_ACCEPTED", base.plusSeconds(1), null);
        insert(uuid(3), "FILE_RECEIVED", base.plusSeconds(2), null);

        var page = adapter.findByCase(caseId, null, 1, 1);

        assertEquals(1, page.size());
        assertEquals(uuid(2), page.get(0).id());
    }

    @Test
    void patientActivityAggregatesOwnedCasesAndFiltersBeforeOrderingAndLimit() {
        UUID secondCaseId = uuid(20);
        UUID otherPatientCaseId = uuid(30);
        insertCase(caseId, patientId, "CAS-PATIENT-1");
        insertCase(secondCaseId, patientId, "CAS-PATIENT-2");
        insertCase(otherPatientCaseId, uuid(99), "CAS-OTHER-1");

        Instant base = Instant.parse("2026-10-24T12:00:00Z");
        insert(uuid(1), "CASE_CREATED", base.plusSeconds(4), null, caseId);
        insert(uuid(2), "CASE_CREATED", base.plusSeconds(4), null, secondCaseId);
        insert(uuid(3), "FILE_RECEIVED", base.plusSeconds(3), null, caseId);
        insert(uuid(4), "CONSENT_ACCEPTED", base.plusSeconds(2), null, secondCaseId);
        UUID availableResultId = uuid(5);
        insert(availableResultId, "RESULT_PUBLISHED", base.plusSeconds(1), availableResultId, secondCaseId);
        jdbc.update("INSERT INTO case_result(id, case_id, status) VALUES (?, ?, 'AVAILABLE')",
                availableResultId, secondCaseId);
        insert(uuid(6), "FILE_RECEIVED", base, null, secondCaseId);
        insert(uuid(7), "NOTIFICATION_SENT", base.plusSeconds(20), null, caseId);
        insert(uuid(8), "CASE_CREATED", base.plusSeconds(30), null, otherPatientCaseId);
        UUID unpublishedResultId = uuid(9);
        insert(unpublishedResultId, "RESULT_PUBLISHED", base.plusSeconds(40), unpublishedResultId, caseId);
        jdbc.update("INSERT INTO case_result(id, case_id, status) VALUES (?, ?, 'UPLOADED')",
                unpublishedResultId, caseId);

        var recent = adapter.findRecentForPatient(patientId,
                Set.of("CASE_CREATED", "CONSENT_ACCEPTED", "FILE_RECEIVED", "RESULT_PUBLISHED"), 5);

        assertEquals(5, recent.size());
        assertEquals(List.of(uuid(2), uuid(1), uuid(3), uuid(4), uuid(5)),
                recent.stream().map(ActivityEventQueryPort.PatientEvent::id).toList());
        assertEquals(List.of("CAS-PATIENT-2", "CAS-PATIENT-1", "CAS-PATIENT-1",
                        "CAS-PATIENT-2", "CAS-PATIENT-2"),
                recent.stream().map(ActivityEventQueryPort.PatientEvent::caseReferenceCode).toList());
    }

    private void insertCase(UUID id, UUID ownerId, String referenceCode) {
        jdbc.update("INSERT INTO medical_case(id, patient_id, reference_code) VALUES (?, ?, ?)",
                id, ownerId, referenceCode);
    }

    private void insert(UUID id, String type, Instant occurredAt, UUID relatedEntityId) {
        insert(id, type, occurredAt, relatedEntityId, caseId);
    }

    private void insert(UUID id, String type, Instant occurredAt, UUID relatedEntityId, UUID targetCaseId) {
        jdbc.update("INSERT INTO activity_event(id, type, occurred_at, case_id, related_entity_id) VALUES (?, ?, ?, ?, ?)",
                id, type, occurredAt, targetCaseId, relatedEntityId);
    }

    private static UUID uuid(long low) {
        return new UUID(0, low);
    }
}
