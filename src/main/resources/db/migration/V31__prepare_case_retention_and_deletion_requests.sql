-- Retention and deletion workflow foundation. No automated case or file purge
-- is enabled by this migration; policy and execution are implemented separately.

ALTER TABLE medical_case
    ADD COLUMN archived_at TIMESTAMP WITH TIME ZONE,
    ADD COLUMN archived_by UUID;

ALTER TABLE medical_case
    ADD CONSTRAINT fk_medical_case_archived_by
        FOREIGN KEY (archived_by) REFERENCES app_user(id) ON DELETE SET NULL;

CREATE INDEX idx_medical_case_archived_at
    ON medical_case(archived_at, id)
    WHERE archived_at IS NOT NULL;

CREATE TABLE case_deletion_request (
    id UUID PRIMARY KEY,
    case_id UUID NOT NULL,
    requested_by UUID,
    decided_by UUID,
    status VARCHAR(20) NOT NULL DEFAULT 'REQUESTED',
    request_code VARCHAR(40) NOT NULL,
    idempotency_key VARCHAR(200) NOT NULL UNIQUE,
    requested_at TIMESTAMP WITH TIME ZONE NOT NULL,
    decided_at TIMESTAMP WITH TIME ZONE,
    eligible_at TIMESTAMP WITH TIME ZONE,
    completed_at TIMESTAMP WITH TIME ZONE,
    attempt_count INTEGER NOT NULL DEFAULT 0,
    last_error_code VARCHAR(60),
    CONSTRAINT fk_case_deletion_request_case
        FOREIGN KEY (case_id) REFERENCES medical_case(id) ON DELETE RESTRICT,
    CONSTRAINT fk_case_deletion_request_requester
        FOREIGN KEY (requested_by) REFERENCES app_user(id) ON DELETE SET NULL,
    CONSTRAINT fk_case_deletion_request_decider
        FOREIGN KEY (decided_by) REFERENCES app_user(id) ON DELETE SET NULL,
    CONSTRAINT chk_case_deletion_request_status CHECK (
        status IN ('REQUESTED', 'APPROVED', 'REJECTED', 'PROCESSING', 'COMPLETED', 'FAILED', 'CANCELLED')
    ),
    CONSTRAINT chk_case_deletion_request_code CHECK (
        request_code IN ('USER_REQUEST', 'RETENTION_EXPIRY', 'ADMINISTRATIVE')
    ),
    CONSTRAINT chk_case_deletion_request_decision CHECK (
        (status IN ('REQUESTED', 'CANCELLED') AND decided_at IS NULL)
        OR (status NOT IN ('REQUESTED', 'CANCELLED') AND decided_at IS NOT NULL)
    ),
    CONSTRAINT chk_case_deletion_request_completion CHECK (
        (status = 'COMPLETED' AND completed_at IS NOT NULL)
        OR (status <> 'COMPLETED' AND completed_at IS NULL)
    ),
    CONSTRAINT chk_case_deletion_request_attempt_count CHECK (attempt_count >= 0)
);

CREATE UNIQUE INDEX uq_case_deletion_request_active_case
    ON case_deletion_request(case_id)
    WHERE status IN ('REQUESTED', 'APPROVED', 'PROCESSING', 'FAILED');

CREATE INDEX idx_case_deletion_request_due
    ON case_deletion_request(status, eligible_at, requested_at)
    WHERE status IN ('APPROVED', 'FAILED') AND eligible_at IS NOT NULL;

CREATE TABLE file_deletion_request (
    id UUID PRIMARY KEY,
    file_id UUID NOT NULL,
    case_id UUID NOT NULL,
    requested_by UUID,
    decided_by UUID,
    status VARCHAR(20) NOT NULL DEFAULT 'REQUESTED',
    request_code VARCHAR(40) NOT NULL,
    idempotency_key VARCHAR(200) NOT NULL UNIQUE,
    requested_at TIMESTAMP WITH TIME ZONE NOT NULL,
    decided_at TIMESTAMP WITH TIME ZONE,
    eligible_at TIMESTAMP WITH TIME ZONE,
    completed_at TIMESTAMP WITH TIME ZONE,
    attempt_count INTEGER NOT NULL DEFAULT 0,
    last_error_code VARCHAR(60),
    CONSTRAINT fk_file_deletion_request_file
        FOREIGN KEY (file_id, case_id) REFERENCES stored_file(id, case_id) ON DELETE RESTRICT,
    CONSTRAINT fk_file_deletion_request_requester
        FOREIGN KEY (requested_by) REFERENCES app_user(id) ON DELETE SET NULL,
    CONSTRAINT fk_file_deletion_request_decider
        FOREIGN KEY (decided_by) REFERENCES app_user(id) ON DELETE SET NULL,
    CONSTRAINT chk_file_deletion_request_status CHECK (
        status IN ('REQUESTED', 'APPROVED', 'REJECTED', 'PROCESSING', 'COMPLETED', 'FAILED', 'CANCELLED')
    ),
    CONSTRAINT chk_file_deletion_request_code CHECK (
        request_code IN ('USER_REQUEST', 'RETENTION_EXPIRY', 'ADMINISTRATIVE')
    ),
    CONSTRAINT chk_file_deletion_request_decision CHECK (
        (status IN ('REQUESTED', 'CANCELLED') AND decided_at IS NULL)
        OR (status NOT IN ('REQUESTED', 'CANCELLED') AND decided_at IS NOT NULL)
    ),
    CONSTRAINT chk_file_deletion_request_completion CHECK (
        (status = 'COMPLETED' AND completed_at IS NOT NULL)
        OR (status <> 'COMPLETED' AND completed_at IS NULL)
    ),
    CONSTRAINT chk_file_deletion_request_attempt_count CHECK (attempt_count >= 0)
);

CREATE UNIQUE INDEX uq_file_deletion_request_active_file
    ON file_deletion_request(file_id)
    WHERE status IN ('REQUESTED', 'APPROVED', 'PROCESSING', 'FAILED');

CREATE INDEX idx_file_deletion_request_due
    ON file_deletion_request(status, eligible_at, requested_at)
    WHERE status IN ('APPROVED', 'FAILED') AND eligible_at IS NOT NULL;

-- Notification evidence must not be removed automatically with a case.
ALTER TABLE notification_inbox
    DROP CONSTRAINT fk_notification_inbox_case;
ALTER TABLE notification_inbox
    ADD CONSTRAINT fk_notification_inbox_case
        FOREIGN KEY (case_id) REFERENCES medical_case(id) ON DELETE RESTRICT;

ALTER TABLE notification_outbox
    DROP CONSTRAINT fk_notification_outbox_case;
ALTER TABLE notification_outbox
    ADD CONSTRAINT fk_notification_outbox_case
        FOREIGN KEY (case_id) REFERENCES medical_case(id) ON DELETE RESTRICT;
