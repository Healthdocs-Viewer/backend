-- Durable case activity. Events are retained with the case and must not be
-- removed implicitly when a case or user is deleted.
CREATE TABLE activity_event (
    id UUID PRIMARY KEY,
    type VARCHAR(50) NOT NULL,
    occurred_at TIMESTAMP WITH TIME ZONE NOT NULL,
    recorded_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    case_id UUID,
    actor_user_id UUID,
    subject_user_id UUID,
    related_entity_type VARCHAR(50),
    related_entity_id UUID,
    metadata JSONB,
    deduplication_key VARCHAR(200) NOT NULL,
    schema_version INTEGER NOT NULL DEFAULT 1,
    CONSTRAINT fk_activity_event_case
        FOREIGN KEY (case_id) REFERENCES medical_case(id) ON DELETE RESTRICT,
    CONSTRAINT fk_activity_event_actor
        FOREIGN KEY (actor_user_id) REFERENCES app_user(id) ON DELETE SET NULL,
    CONSTRAINT fk_activity_event_subject
        FOREIGN KEY (subject_user_id) REFERENCES app_user(id) ON DELETE SET NULL,
    CONSTRAINT uq_activity_event_deduplication_key UNIQUE (deduplication_key),
    CONSTRAINT chk_activity_event_type CHECK (type IN (
        'CASE_CREATED',
        'CONSENT_ACCEPTED',
        'FILE_RECEIVED',
        'CASE_PROCESSING_ENABLED',
        'RESEARCHER_ASSIGNED',
        'RESEARCHER_UNASSIGNED',
        'RESULT_UPLOADED',
        'RESULT_PUBLISHED',
        'RESULT_CORRECTION_REQUESTED',
        'RESULT_CORRECTION_UPLOADED',
        'RESULT_DELETED',
        'INPUT_CHANGE_OPENED',
        'INPUT_CHANGE_FILE_ADDED',
        'INPUT_CHANGE_FILE_REMOVED',
        'INPUT_CHANGE_SUBMITTED',
        'INPUT_CHANGE_APPROVED',
        'INPUT_CHANGE_REJECTED',
        'INPUT_CHANGE_CANCELLED'
    )),
    CONSTRAINT chk_activity_event_schema_version CHECK (schema_version > 0),
    CONSTRAINT chk_activity_event_related_entity CHECK (
        (related_entity_type IS NULL AND related_entity_id IS NULL)
        OR (related_entity_type IS NOT NULL AND related_entity_id IS NOT NULL)
    ),
    CONSTRAINT chk_activity_event_metadata_size CHECK (
        metadata IS NULL OR octet_length(metadata::text) <= 2048
    )
);

CREATE INDEX idx_activity_event_case_occurred
    ON activity_event(case_id, occurred_at DESC, id DESC)
    WHERE case_id IS NOT NULL;

CREATE INDEX idx_activity_event_subject_occurred
    ON activity_event(subject_user_id, occurred_at DESC, id DESC)
    WHERE subject_user_id IS NOT NULL;
