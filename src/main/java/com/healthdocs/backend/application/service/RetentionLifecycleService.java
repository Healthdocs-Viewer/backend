package com.healthdocs.backend.application.service;

import com.healthdocs.backend.application.port.out.ActivityEventPort;
import com.healthdocs.backend.application.port.out.AuthProvider.AuthenticatedUser;
import com.healthdocs.backend.domain.model.Role;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Case archival and auditable deletion-request workflow. This service never purges data. */
@Service
public class RetentionLifecycleService {
    private final JdbcTemplate jdbc;
    private final ActivityEventPort activity;
    private final Clock clock;

    public RetentionLifecycleService(JdbcTemplate jdbc, ActivityEventPort activity, Clock clock) {
        this.jdbc = jdbc;
        this.activity = activity;
        this.clock = clock;
    }

    @Transactional
    public CaseArchive archiveCase(AuthenticatedUser actor, UUID caseId) {
        requireAdmin(actor);
        List<CaseArchive> cases = jdbc.query("""
                SELECT id, archived_at, archived_by FROM medical_case WHERE id = ? FOR UPDATE
                """, (rs, row) -> new CaseArchive(rs.getObject("id", UUID.class),
                instant(rs, "archived_at"), rs.getObject("archived_by", UUID.class)), caseId);
        if (cases.isEmpty()) throw new ResourceNotFoundException("Case not found");
        CaseArchive existing = cases.getFirst();
        if (existing.archivedAt() != null) return existing;

        Instant now = clock.instant();
        jdbc.update("UPDATE medical_case SET archived_at = ?, archived_by = ? WHERE id = ? AND archived_at IS NULL",
                Timestamp.from(now), actor.id(), caseId);
        activity.record("CASE_ARCHIVED", now, caseId, actor.id(), null, "CASE", caseId,
                "CASE_ARCHIVED:" + caseId);
        return new CaseArchive(caseId, now, actor.id());
    }

    @Transactional
    public DeletionRequest requestCaseDeletion(AuthenticatedUser actor, UUID caseId, UUID idempotencyKey) {
        requirePatient(actor);
        requireIdempotencyKey(idempotencyKey);
        UUID owner = lockCaseOwner(caseId);
        if (!actor.id().equals(owner)) throw new ResourceNotFoundException("Case not found");
        String key = "CASE:" + actor.id() + ":" + caseId + ":" + idempotencyKey;
        DeletionRequest replay = findByKey("case_deletion_request", key);
        if (replay != null) return replay.asReplay();
        if (hasActiveRequest("case_deletion_request", "case_id", caseId)) {
            throw new BusinessRuleViolationException("An active deletion request already exists for this case");
        }

        Instant now = clock.instant();
        UUID requestId = UUID.randomUUID();
        insertRequest("case_deletion_request", requestId, caseId, null, actor.id(), key, now);
        activity.record("CASE_DELETION_REQUESTED", now, caseId, actor.id(), actor.id(), "CASE", caseId,
                "CASE_DELETION_REQUESTED:" + requestId);
        return requestView("case_deletion_request", requestId);
    }

    @Transactional
    public DeletionRequest requestFileDeletion(AuthenticatedUser actor, UUID caseId, UUID fileId,
            UUID idempotencyKey) {
        requirePatient(actor);
        requireIdempotencyKey(idempotencyKey);
        List<UUID> owners = jdbc.query("""
                SELECT c.patient_id FROM stored_file f JOIN medical_case c ON c.id = f.case_id
                WHERE f.id = ? AND f.case_id = ? FOR UPDATE OF c
                """, (rs, row) -> rs.getObject(1, UUID.class), fileId, caseId);
        if (owners.isEmpty() || !actor.id().equals(owners.getFirst())) {
            throw new ResourceNotFoundException("File not found");
        }
        String key = "FILE:" + actor.id() + ":" + fileId + ":" + idempotencyKey;
        DeletionRequest replay = findByKey("file_deletion_request", key);
        if (replay != null) return replay.asReplay();
        if (hasActiveRequest("file_deletion_request", "file_id", fileId)) {
            throw new BusinessRuleViolationException("An active deletion request already exists for this file");
        }

        Instant now = clock.instant();
        UUID requestId = UUID.randomUUID();
        insertRequest("file_deletion_request", requestId, caseId, fileId, actor.id(), key, now);
        activity.record("FILE_DELETION_REQUESTED", now, caseId, actor.id(), actor.id(), "FILE", fileId,
                "FILE_DELETION_REQUESTED:" + requestId);
        return requestView("file_deletion_request", requestId);
    }

    @Transactional
    public DeletionRequest decideCaseDeletion(AuthenticatedUser actor, UUID requestId, Decision decision) {
        return decide(actor, "case_deletion_request", requestId, decision, "CASE");
    }

    @Transactional
    public DeletionRequest decideFileDeletion(AuthenticatedUser actor, UUID requestId, Decision decision) {
        return decide(actor, "file_deletion_request", requestId, decision, "FILE");
    }

    @Transactional(readOnly = true)
    public DeletionRequestPage listDeletionRequests(AuthenticatedUser actor, String targetType, String status,
            int page, int size) {
        requireAdmin(actor);
        String table = tableFor(targetType);
        if (page < 0) throw new IllegalArgumentException("Page must be zero or greater");
        if (size < 1 || size > 100) throw new IllegalArgumentException("Size must be between 1 and 100");
        if (status != null && !List.of("REQUESTED", "APPROVED", "REJECTED", "PROCESSING", "COMPLETED",
                "FAILED", "CANCELLED").contains(status)) {
            throw new IllegalArgumentException("Unsupported deletion request status");
        }
        long offset = (long) page * size;
        String statusPredicate = status == null ? "" : " WHERE status = ?";
        Object[] args = status == null ? new Object[0] : new Object[] { status };
        Long total = jdbc.queryForObject("SELECT COUNT(*) FROM " + table + statusPredicate, Long.class, args);
        String sql = "SELECT id, case_id, " + fileColumn(table)
                + " AS file_id, status, requested_at, decided_at, eligible_at, completed_at FROM " + table
                + statusPredicate + " ORDER BY requested_at DESC, id DESC LIMIT ? OFFSET ?";
        Object[] pageArgs = status == null ? new Object[] { size, offset }
                : new Object[] { status, size, offset };
        List<DeletionRequest> items = jdbc.query(sql, this::mapRequest, pageArgs);
        int pages = total == 0 ? 0 : (int) ((total + size - 1) / size);
        return new DeletionRequestPage(items, page, size, total, pages);
    }

    private DeletionRequest decide(AuthenticatedUser actor, String table, UUID requestId, Decision decision,
            String targetType) {
        requireAdmin(actor);
        if (decision == null) throw new IllegalArgumentException("Decision is required");
        List<DeletionRequest> rows = jdbc.query("""
                SELECT id, case_id, %s AS file_id, status, requested_at, decided_at, eligible_at, completed_at
                FROM %s WHERE id = ? FOR UPDATE
                """.formatted(fileColumn(table), table), this::mapRequest, requestId);
        if (rows.isEmpty()) throw new ResourceNotFoundException("Deletion request not found");
        DeletionRequest current = rows.getFirst();
        String desiredStatus = decision == Decision.APPROVE ? "APPROVED" : "REJECTED";
        if (desiredStatus.equals(current.status())) return current;
        if (!"REQUESTED".equals(current.status())) {
            throw new BusinessRuleViolationException("Deletion request is no longer awaiting a decision");
        }

        Instant now = clock.instant();
        jdbc.update("UPDATE " + table + " SET status = ?, decided_by = ?, decided_at = ? WHERE id = ?",
                desiredStatus, actor.id(), Timestamp.from(now), requestId);
        String eventType = targetType + "_DELETION_" + (decision == Decision.APPROVE ? "APPROVED" : "REJECTED");
        activity.record(eventType, now, current.caseId(), actor.id(), null, targetType, current.targetId(),
                eventType + ":" + requestId);
        return requestView(table, requestId);
    }

    private UUID lockCaseOwner(UUID caseId) {
        List<UUID> owners = jdbc.query("SELECT patient_id FROM medical_case WHERE id = ? FOR UPDATE",
                (rs, row) -> rs.getObject(1, UUID.class), caseId);
        if (owners.isEmpty()) throw new ResourceNotFoundException("Case not found");
        return owners.getFirst();
    }

    private void insertRequest(String table, UUID requestId, UUID caseId, UUID fileId, UUID actorId,
            String idempotencyKey, Instant now) {
        String columns = "case_id, requested_by";
        String values = "?, ?";
        if ("file_deletion_request".equals(table)) {
            columns = "file_id, case_id, requested_by";
            values = "?, ?, ?";
        }
        String sql = "INSERT INTO " + table + " (id, " + columns
                + ", status, request_code, idempotency_key, requested_at) VALUES (?, " + values
                + ", 'REQUESTED', 'USER_REQUEST', ?, ? )";
        if ("file_deletion_request".equals(table)) {
            jdbc.update(sql, requestId, fileId, caseId, actorId, idempotencyKey, Timestamp.from(now));
        } else {
            jdbc.update(sql, requestId, caseId, actorId, idempotencyKey, Timestamp.from(now));
        }
    }

    private DeletionRequest findByKey(String table, String key) {
        List<DeletionRequest> rows = jdbc.query("""
                SELECT id, case_id, %s AS file_id, status, requested_at, decided_at, eligible_at, completed_at
                FROM %s WHERE idempotency_key = ?
                """.formatted(fileColumn(table), table), this::mapRequest, key);
        return rows.isEmpty() ? null : rows.getFirst();
    }

    private boolean hasActiveRequest(String table, String targetColumn, UUID targetId) {
        Boolean found = jdbc.queryForObject("SELECT EXISTS (SELECT 1 FROM " + table + " WHERE " + targetColumn
                + " = ? AND status IN ('REQUESTED', 'APPROVED', 'PROCESSING', 'FAILED'))",
                Boolean.class, targetId);
        return Boolean.TRUE.equals(found);
    }

    private DeletionRequest requestView(String table, UUID requestId) {
        List<DeletionRequest> rows = jdbc.query("""
                SELECT id, case_id, %s AS file_id, status, requested_at, decided_at, eligible_at, completed_at
                FROM %s WHERE id = ?
                """.formatted(fileColumn(table), table), this::mapRequest, requestId);
        if (rows.isEmpty()) throw new ResourceNotFoundException("Deletion request not found");
        return rows.getFirst();
    }

    private DeletionRequest mapRequest(ResultSet rs, int row) throws SQLException {
        UUID fileId = rs.getObject("file_id", UUID.class);
        return new DeletionRequest(rs.getObject("id", UUID.class), rs.getObject("case_id", UUID.class), fileId,
                rs.getString("status"), instant(rs, "requested_at"), instant(rs, "decided_at"),
                instant(rs, "eligible_at"), instant(rs, "completed_at"), fileId == null ? "CASE" : "FILE", false);
    }

    private static String fileColumn(String table) {
        return "file_deletion_request".equals(table) ? "file_id" : "NULL::uuid";
    }

    private static String tableFor(String targetType) {
        if ("CASE".equals(targetType)) return "case_deletion_request";
        if ("FILE".equals(targetType)) return "file_deletion_request";
        throw new IllegalArgumentException("Target type must be CASE or FILE");
    }

    private static Instant instant(ResultSet rs, String column) throws SQLException {
        Timestamp timestamp = rs.getTimestamp(column);
        return timestamp == null ? null : timestamp.toInstant();
    }

    private static void requireAdmin(AuthenticatedUser actor) {
        if (actor == null || !actor.enabled() || actor.role() != Role.ADMIN) {
            throw new ForbiddenOperationException("Enabled administrator access is required");
        }
    }

    private static void requirePatient(AuthenticatedUser actor) {
        if (actor == null || !actor.enabled() || actor.role() != Role.PATIENT) {
            throw new ForbiddenOperationException("Enabled patient access is required");
        }
    }

    private static void requireIdempotencyKey(UUID idempotencyKey) {
        if (idempotencyKey == null) throw new IllegalArgumentException("Idempotency-Key is required");
    }

    public enum Decision { APPROVE, REJECT }

    public record CaseArchive(UUID caseId, Instant archivedAt, UUID archivedBy) { }

    public record DeletionRequestPage(List<DeletionRequest> items, int page, int size, long totalElements,
            int totalPages) { }

    public record DeletionRequest(UUID id, UUID caseId, UUID fileId, String status, Instant requestedAt,
            Instant decidedAt, Instant eligibleAt, Instant completedAt, String targetType, boolean replayed) {
        DeletionRequest asReplay() {
            return new DeletionRequest(id, caseId, fileId, status, requestedAt, decidedAt, eligibleAt,
                    completedAt, targetType, true);
        }

        UUID targetId() { return fileId == null ? caseId : fileId; }
    }
}
