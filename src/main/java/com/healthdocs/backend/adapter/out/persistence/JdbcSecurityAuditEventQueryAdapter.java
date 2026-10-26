package com.healthdocs.backend.adapter.out.persistence;

import com.healthdocs.backend.application.port.out.SecurityAuditEventQueryPort;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcSecurityAuditEventQueryAdapter implements SecurityAuditEventQueryPort {
    private final NamedParameterJdbcTemplate jdbc;

    public JdbcSecurityAuditEventQueryAdapter(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Page find(Filter filter, int limit, long offset) {
        String where = where(filter);
        MapSqlParameterSource params = parameters(filter);
        Long total = jdbc.queryForObject("SELECT COUNT(*) FROM security_audit_event" + where, params, Long.class);
        params.addValue("limit", limit).addValue("offset", offset);
        List<Event> events = jdbc.query("""
                SELECT id, type, occurred_at, actor_user_id, subject_user_id, case_id,
                       resource_type, resource_id, action, changed_field, previous_value, new_value
                FROM security_audit_event
                """ + where + " ORDER BY occurred_at DESC, id DESC LIMIT :limit OFFSET :offset", params,
                (rs, rowNum) -> new Event(rs.getObject("id", UUID.class), rs.getString("type"),
                        rs.getTimestamp("occurred_at").toInstant(), rs.getObject("actor_user_id", UUID.class),
                        rs.getObject("subject_user_id", UUID.class), rs.getObject("case_id", UUID.class),
                        rs.getString("resource_type"), rs.getObject("resource_id", UUID.class),
                        rs.getString("action"), rs.getString("changed_field"), rs.getString("previous_value"),
                        rs.getString("new_value")));
        return new Page(events, total == null ? 0 : total);
    }

    private static String where(Filter filter) {
        List<String> clauses = new ArrayList<>();
        if (filter.type() != null) clauses.add("type = :type");
        if (filter.actorUserId() != null) clauses.add("actor_user_id = :actorUserId");
        if (filter.subjectUserId() != null) clauses.add("subject_user_id = :subjectUserId");
        if (filter.resourceType() != null) clauses.add("resource_type = :resourceType");
        if (filter.resourceId() != null) clauses.add("resource_id = :resourceId");
        return clauses.isEmpty() ? "" : " WHERE " + String.join(" AND ", clauses);
    }

    private static MapSqlParameterSource parameters(Filter filter) {
        MapSqlParameterSource params = new MapSqlParameterSource();
        if (filter.type() != null) params.addValue("type", filter.type());
        if (filter.actorUserId() != null) params.addValue("actorUserId", filter.actorUserId());
        if (filter.subjectUserId() != null) params.addValue("subjectUserId", filter.subjectUserId());
        if (filter.resourceType() != null) params.addValue("resourceType", filter.resourceType());
        if (filter.resourceId() != null) params.addValue("resourceId", filter.resourceId());
        return params;
    }
}
