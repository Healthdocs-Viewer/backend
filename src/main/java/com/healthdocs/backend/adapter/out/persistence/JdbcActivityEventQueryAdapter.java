package com.healthdocs.backend.adapter.out.persistence;

import com.healthdocs.backend.application.port.out.ActivityEventQueryPort;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcActivityEventQueryAdapter implements ActivityEventQueryPort {
    private static final String PATIENT_RESULT_VISIBILITY = """
            AND (type <> 'RESULT_PUBLISHED' OR EXISTS (
                SELECT 1 FROM case_result result
                WHERE result.id = activity_event.related_entity_id
                  AND result.case_id = activity_event.case_id
                  AND result.status = 'AVAILABLE'
                  AND result.deleted_at IS NULL
            ))
            """;
    private final JdbcTemplate jdbc;

    public JdbcActivityEventQueryAdapter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<Event> findByCase(UUID caseId, Set<String> visibleTypes, int limit, long offset) {
        String typeClause = typeClause(visibleTypes);
        String visibilityClause = visibleTypes == null ? "" : PATIENT_RESULT_VISIBILITY;
        String sql = "SELECT id, type, occurred_at FROM activity_event WHERE case_id = ?"
                + typeClause + visibilityClause + " ORDER BY occurred_at DESC, id DESC LIMIT ? OFFSET ?";
        Object[] parameters = parameters(caseId, visibleTypes);
        Object[] paged = java.util.Arrays.copyOf(parameters, parameters.length + 2);
        paged[parameters.length] = limit;
        paged[parameters.length + 1] = offset;
        return jdbc.query(sql, (rs, rowNum) -> map(rs), paged);
    }

    @Override
    public long countByCase(UUID caseId, Set<String> visibleTypes) {
        String typeClause = typeClause(visibleTypes);
        String visibilityClause = visibleTypes == null ? "" : PATIENT_RESULT_VISIBILITY;
        Long count = jdbc.queryForObject("SELECT COUNT(*) FROM activity_event WHERE case_id = ?"
                        + typeClause + visibilityClause,
                Long.class, parameters(caseId, visibleTypes));
        return count == null ? 0 : count;
    }

    @Override
    public List<PatientEvent> findRecentForPatient(UUID patientId, Set<String> visibleTypes, int limit) {
        String typeClause = typeClause(visibleTypes);
        String visibilityClause = visibleTypes == null ? "" : PATIENT_RESULT_VISIBILITY;
        String sql = """
                SELECT activity_event.id, activity_event.type, activity_event.occurred_at,
                       medical_case.reference_code
                FROM activity_event
                JOIN medical_case ON medical_case.id = activity_event.case_id
                WHERE medical_case.patient_id = ?
                """ + typeClause + visibilityClause
                + " ORDER BY activity_event.occurred_at DESC, activity_event.id DESC LIMIT ?";
        Object[] base = parameters(patientId, visibleTypes);
        Object[] paged = java.util.Arrays.copyOf(base, base.length + 1);
        paged[base.length] = limit;
        return jdbc.query(sql, (rs, rowNum) -> new PatientEvent(
                rs.getObject("id", UUID.class), rs.getString("type"),
                rs.getTimestamp("occurred_at").toInstant(), rs.getString("reference_code")), paged);
    }

    private static String typeClause(Set<String> visibleTypes) {
        if (visibleTypes == null) return "";
        if (visibleTypes.isEmpty()) return " AND 1 = 0";
        return " AND activity_event.type IN (" + String.join(",", java.util.Collections.nCopies(visibleTypes.size(), "?")) + ")";
    }

    private static Object[] parameters(UUID caseId, Set<String> visibleTypes) {
        if (visibleTypes == null || visibleTypes.isEmpty()) return new Object[] { caseId };
        Object[] values = new Object[1 + visibleTypes.size()];
        values[0] = caseId;
        int index = 1;
        for (String type : visibleTypes) values[index++] = type;
        return values;
    }

    private static Event map(ResultSet rs) throws SQLException {
        return new Event(rs.getObject("id", UUID.class), rs.getString("type"),
                rs.getTimestamp("occurred_at").toInstant());
    }
}
