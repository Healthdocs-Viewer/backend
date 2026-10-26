package com.healthdocs.backend.adapter.out.persistence.entity;

import com.healthdocs.backend.domain.model.InputChangeFileSet;
import com.healthdocs.backend.domain.model.FileKind;
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
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "input_change_request_file")
public class InputChangeRequestFileEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "request_id", nullable = false)
    private InputChangeRequestEntity request;

    @Column(name = "case_id", nullable = false, updatable = false)
    private UUID caseId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "file_id", nullable = false)
    private StoredFileEntity file;

    @Enumerated(EnumType.STRING)
    @Column(name = "file_set", nullable = false, length = 20)
    private InputChangeFileSet fileSet;

    @Column(name = "added_at", nullable = false, updatable = false)
    private Instant addedAt;

    @Column(name = "added_by", nullable = false, updatable = false)
    private UUID addedBy;

    @Column(name = "removed_at")
    private Instant removedAt;

    @Column(name = "removed_by")
    private UUID removedBy;

    protected InputChangeRequestFileEntity() {
    }

    InputChangeRequestFileEntity(InputChangeRequestEntity request, StoredFileEntity file,
            InputChangeFileSet fileSet, UUID actorId, Instant at) {
        if (!request.getMedicalCase().getId().equals(file.getMedicalCase().getId())) {
            throw new IllegalArgumentException("Request file must belong to the same case");
        }
        if (file.getFileKind() != FileKind.INPUT) {
            throw new IllegalArgumentException("Request files must be INPUT files");
        }
        if (fileSet == null || actorId == null || at == null) {
            throw new IllegalArgumentException("File set, actor and timestamp are required");
        }
        this.request = request;
        this.caseId = request.getMedicalCase().getId();
        this.file = file;
        this.fileSet = fileSet;
        this.addedBy = actorId;
        this.addedAt = at;
    }

    void remove(UUID actorId, Instant at) {
        if (fileSet != InputChangeFileSet.PROPOSED) {
            throw new IllegalStateException("Original snapshot files cannot be removed");
        }
        if (removedAt != null) {
            throw new IllegalStateException("Proposed file is already removed");
        }
        this.removedBy = actorId;
        this.removedAt = at;
    }

    public UUID getId() { return id; }
    public InputChangeRequestEntity getRequest() { return request; }
    public UUID getCaseId() { return caseId; }
    public StoredFileEntity getFile() { return file; }
    public InputChangeFileSet getFileSet() { return fileSet; }
    public Instant getAddedAt() { return addedAt; }
    public UUID getAddedBy() { return addedBy; }
    public Instant getRemovedAt() { return removedAt; }
    public UUID getRemovedBy() { return removedBy; }
    public boolean isActive() { return removedAt == null; }
}
