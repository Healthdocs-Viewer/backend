package com.healthdocs.backend.adapter.out.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;

@Entity
@Table(name = "case_assignment")
@IdClass(CaseAssignmentEntity.AssignmentId.class)
public class CaseAssignmentEntity {

    @Id
    @Column(name = "case_id")
    private UUID caseId;

    @Id
    @Column(name = "user_id")
    private UUID userId;

    @CreationTimestamp
    @Column(name = "assigned_at", nullable = false, updatable = false)
    private Instant assignedAt;

    protected CaseAssignmentEntity() {
    }

    public CaseAssignmentEntity(UUID caseId, UUID userId) {
        this.caseId = caseId;
        this.userId = userId;
    }

    public UUID getCaseId() { return caseId; }
    public UUID getUserId() { return userId; }
    public Instant getAssignedAt() { return assignedAt; }

    public static class AssignmentId implements Serializable {
        private UUID caseId;
        private UUID userId;

        public AssignmentId() {
        }

        public AssignmentId(UUID caseId, UUID userId) {
            this.caseId = caseId;
            this.userId = userId;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof AssignmentId that)) {
                return false;
            }
            return caseId.equals(that.caseId) && userId.equals(that.userId);
        }

        @Override
        public int hashCode() {
            return java.util.Objects.hash(caseId, userId);
        }
    }
}
