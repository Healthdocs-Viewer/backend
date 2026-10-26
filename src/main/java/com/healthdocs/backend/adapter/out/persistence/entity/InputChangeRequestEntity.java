package com.healthdocs.backend.adapter.out.persistence.entity;

import com.healthdocs.backend.domain.model.CaseStatus;
import com.healthdocs.backend.domain.model.InputChangeFileSet;
import com.healthdocs.backend.domain.model.InputChangeRequestStatus;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

@Entity
@Table(name = "input_change_request")
public class InputChangeRequestEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "case_id", nullable = false)
    private MedicalCaseEntity medicalCase;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "requested_by", nullable = false)
    private AppUserEntity requestedBy;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private InputChangeRequestStatus status;

    @Column(length = 250)
    private String reason;

    @Enumerated(EnumType.STRING)
    @Column(name = "resume_case_status", nullable = false, length = 50)
    private CaseStatus resumeCaseStatus;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "submitted_at")
    private Instant submittedAt;

    @Column(name = "decided_at")
    private Instant decidedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "decided_by")
    private AppUserEntity decidedBy;

    @Column(name = "decision_reason", length = 1000)
    private String decisionReason;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cancelled_by")
    private AppUserEntity cancelledBy;

    @OneToMany(mappedBy = "request", cascade = CascadeType.PERSIST)
    private List<InputChangeRequestFileEntity> files = new ArrayList<>();

    protected InputChangeRequestEntity() {
    }

    public InputChangeRequestEntity(MedicalCaseEntity medicalCase, AppUserEntity requestedBy,
            CaseStatus resumeCaseStatus) {
        if (medicalCase == null || requestedBy == null) {
            throw new IllegalArgumentException("Case and owner are required");
        }
        if (resumeCaseStatus != CaseStatus.FILES_RECEIVED
                && resumeCaseStatus != CaseStatus.ENABLED_FOR_PROCESSING) {
            throw new IllegalArgumentException("Input changes can only resume FILES_RECEIVED or ENABLED_FOR_PROCESSING");
        }
        if (!medicalCase.getPatient().getId().equals(requestedBy.getId())) {
            throw new IllegalArgumentException("Request owner must own the case");
        }
        if (medicalCase.getStatus() != resumeCaseStatus) {
            throw new IllegalArgumentException("Resume status must match the case status when the request opens");
        }
        this.medicalCase = medicalCase;
        this.requestedBy = requestedBy;
        this.resumeCaseStatus = resumeCaseStatus;
        this.status = InputChangeRequestStatus.DRAFT;
    }

    public void updateReason(String reason) {
        requireStatus(InputChangeRequestStatus.DRAFT);
        if (reason != null && reason.length() > 250) {
            throw new IllegalArgumentException("Reason cannot exceed 250 characters");
        }
        this.reason = reason == null || reason.isBlank() ? null : reason.trim();
    }

    public InputChangeRequestFileEntity addFile(StoredFileEntity file, InputChangeFileSet fileSet, UUID actorId, Instant at) {
        requireStatus(InputChangeRequestStatus.DRAFT);
        if (fileSet == null) throw new IllegalArgumentException("File set is required");
        InputChangeRequestFileEntity existing = files.stream().filter(membership -> membership.isActive()
                && membership.getFileSet() == fileSet && membership.getFile().getId().equals(file.getId()))
                .findFirst().orElse(null);
        if (existing != null) return existing;
        InputChangeRequestFileEntity membership = new InputChangeRequestFileEntity(this, file, fileSet, actorId, at);
        files.add(membership);
        return membership;
    }

    public void initializeFileSets(Collection<StoredFileEntity> activeInputFiles, UUID ownerId, Instant at) {
        requireStatus(InputChangeRequestStatus.DRAFT);
        if (!files.isEmpty()) throw new IllegalStateException("File sets have already been initialized");
        if (activeInputFiles == null || activeInputFiles.isEmpty()) {
            throw new IllegalArgumentException("At least one original INPUT file is required");
        }
        for (StoredFileEntity file : activeInputFiles) {
            addFile(file, InputChangeFileSet.ORIGINAL, ownerId, at);
            addFile(file, InputChangeFileSet.PROPOSED, ownerId, at);
        }
    }

    public InputChangeRequestFileEntity removeProposedFile(UUID fileId, UUID actorId, Instant at) {
        requireStatus(InputChangeRequestStatus.DRAFT);
        InputChangeRequestFileEntity membership = files.stream()
                .filter(file -> file.getFileSet() == InputChangeFileSet.PROPOSED
                        && file.isActive() && file.getFile().getId().equals(fileId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Active proposed file not found"));
        membership.remove(actorId, at);
        return membership;
    }

    public void submit(Instant at) {
        requireStatus(InputChangeRequestStatus.DRAFT);
        if (at == null) throw new IllegalArgumentException("Submission timestamp is required");
        if (reason == null || reason.isBlank()) throw new IllegalStateException("A reason is required before submission");
        if (proposedFiles().isEmpty()) throw new IllegalStateException("At least one proposed file is required");
        this.status = InputChangeRequestStatus.PENDING_REVIEW;
        this.submittedAt = at;
    }

    public void decide(boolean approved, AppUserEntity coordinator, String decisionReason, Instant at) {
        requireStatus(InputChangeRequestStatus.PENDING_REVIEW);
        if (coordinator == null || at == null) throw new IllegalArgumentException("Coordinator and decision timestamp are required");
        if (coordinator.getId().equals(requestedBy.getId())) {
            throw new IllegalArgumentException("The owner cannot decide their own request");
        }
        if (!approved && (decisionReason == null || decisionReason.isBlank())) {
            throw new IllegalArgumentException("A rejection reason is required");
        }
        if (decisionReason != null && decisionReason.length() > 1000) {
            throw new IllegalArgumentException("Decision reason cannot exceed 1000 characters");
        }
        this.status = approved ? InputChangeRequestStatus.APPROVED : InputChangeRequestStatus.REJECTED;
        this.decidedBy = coordinator;
        this.decidedAt = at;
        this.decisionReason = decisionReason == null || decisionReason.isBlank() ? null : decisionReason.trim();
    }

    public void cancel(AppUserEntity owner, Instant at) {
        requireStatus(InputChangeRequestStatus.DRAFT);
        if (owner == null || at == null) throw new IllegalArgumentException("Owner and cancellation timestamp are required");
        if (!requestedBy.getId().equals(owner.getId())) throw new IllegalArgumentException("Only the owner can cancel");
        files.stream().filter(file -> file.getFileSet() == InputChangeFileSet.PROPOSED && file.isActive())
                .forEach(file -> file.remove(owner.getId(), at));
        this.status = InputChangeRequestStatus.CANCELLED;
        this.cancelledBy = owner;
        this.cancelledAt = at;
    }

    public List<InputChangeRequestFileEntity> originalFiles() {
        return files.stream().filter(file -> file.getFileSet() == InputChangeFileSet.ORIGINAL && file.isActive()).toList();
    }

    public List<InputChangeRequestFileEntity> proposedFiles() {
        return files.stream().filter(file -> file.getFileSet() == InputChangeFileSet.PROPOSED && file.isActive()).toList();
    }

    private void requireStatus(InputChangeRequestStatus expected) {
        if (status != expected) throw new IllegalStateException("Request must be " + expected);
    }

    public UUID getId() { return id; }
    public MedicalCaseEntity getMedicalCase() { return medicalCase; }
    public AppUserEntity getRequestedBy() { return requestedBy; }
    public InputChangeRequestStatus getStatus() { return status; }
    public String getReason() { return reason; }
    public CaseStatus getResumeCaseStatus() { return resumeCaseStatus; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public Instant getSubmittedAt() { return submittedAt; }
    public Instant getDecidedAt() { return decidedAt; }
    public AppUserEntity getDecidedBy() { return decidedBy; }
    public String getDecisionReason() { return decisionReason; }
    public Instant getCancelledAt() { return cancelledAt; }
    public AppUserEntity getCancelledBy() { return cancelledBy; }
    public List<InputChangeRequestFileEntity> getFiles() { return List.copyOf(files); }
}
