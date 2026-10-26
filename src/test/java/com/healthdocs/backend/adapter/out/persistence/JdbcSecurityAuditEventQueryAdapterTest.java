package com.healthdocs.backend.adapter.out.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.healthdocs.backend.application.port.out.SecurityAuditEventQueryPort.Filter;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

class JdbcSecurityAuditEventQueryAdapterTest {
    private JdbcTemplate jdbc;
    private JdbcSecurityAuditEventQueryAdapter adapter;

    @BeforeEach
    void setUp() {
        var ds = new DriverManagerDataSource(
                "jdbc:h2:mem:security-audit-query;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE", "sa", "");
        jdbc = new JdbcTemplate(ds);
        jdbc.execute("DROP ALL OBJECTS");
        jdbc.execute("CREATE TABLE security_audit_event (id UUID PRIMARY KEY, type VARCHAR(60), occurred_at TIMESTAMP WITH TIME ZONE, actor_user_id UUID, subject_user_id UUID, case_id UUID, resource_type VARCHAR(30), resource_id UUID, action VARCHAR(40), changed_field VARCHAR(40), previous_value VARCHAR(30), new_value VARCHAR(30))");
        adapter = new JdbcSecurityAuditEventQueryAdapter(new NamedParameterJdbcTemplate(ds));
    }

    @Test
    void filtersAndOrdersByOccurrenceThenIdAndAppliesPagination() {
        UUID actor = uuid(10), subject = uuid(20), resource = uuid(30);
        Instant t = Instant.parse("2026-10-24T12:00:00Z");
        insert(uuid(1), "ACCOUNT_ROLE_CHANGED", t, actor, subject, null, null);
        insert(uuid(2), "ACCOUNT_ROLE_CHANGED", t, actor, subject, null, null);
        insert(uuid(3), "SENSITIVE_RESOURCE_ACCESS_GRANTED", t.plusSeconds(1), actor, subject,
                "RESULT", resource);
        insert(uuid(4), "ACCOUNT_CREATED", t.plusSeconds(2), uuid(99), subject, null, null);

        var result = adapter.find(new Filter("ACCOUNT_ROLE_CHANGED", actor, subject, null, null), 1, 1);

        assertEquals(2, result.totalElements());
        assertEquals(1, result.events().size());
        assertEquals(uuid(1), result.events().get(0).id());
    }

    private void insert(UUID id, String type, Instant at, UUID actor, UUID subject,
            String resourceType, UUID resourceId) {
        jdbc.update("INSERT INTO security_audit_event(id,type,occurred_at,actor_user_id,subject_user_id,resource_type,resource_id) VALUES(?,?,?,?,?,?,?)",
                id, type, at, actor, subject, resourceType, resourceId);
    }

    private static UUID uuid(long n) { return new UUID(0, n); }
}
