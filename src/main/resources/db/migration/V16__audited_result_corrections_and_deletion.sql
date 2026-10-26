ALTER TABLE case_result
    ADD COLUMN correction_request TEXT,
    ADD COLUMN correction_reason TEXT,
    ADD COLUMN deleted_at TIMESTAMP WITH TIME ZONE,
    ADD COLUMN deleted_by UUID REFERENCES app_user(id),
    ADD COLUMN deletion_reason TEXT;

ALTER TABLE case_result
    DROP CONSTRAINT chk_case_result_current_is_published;

ALTER TABLE case_result
    ADD CONSTRAINT chk_case_result_current_status CHECK (
        NOT is_current OR status IN ('AVAILABLE', 'CORRECTION_REQUIRED')
    ),
    ADD CONSTRAINT chk_case_result_deletion_audit CHECK (
        (deleted_at IS NULL AND deleted_by IS NULL AND deletion_reason IS NULL)
        OR (deleted_at IS NOT NULL AND deleted_by IS NOT NULL
            AND deletion_reason IS NOT NULL AND length(trim(deletion_reason)) > 0
            AND NOT is_current)
    ),
    ADD CONSTRAINT chk_case_result_correction_request CHECK (
        (status = 'CORRECTION_REQUIRED' AND correction_request IS NOT NULL
            AND length(trim(correction_request)) > 0)
        OR (status <> 'CORRECTION_REQUIRED' AND correction_request IS NULL)
    );

CREATE INDEX idx_case_result_case_active_current
    ON case_result(case_id, is_current)
    WHERE deleted_at IS NULL;

CREATE UNIQUE INDEX uq_case_result_one_pending_per_case
    ON case_result(case_id)
    WHERE status = 'UPLOADED' AND deleted_at IS NULL;
