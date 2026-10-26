-- Append-only technical telemetry kept apart from case activity, account audit,
-- notification inbox, and delivery state. No automatic telemetry retention job
-- is enabled here.
CREATE TABLE technical_operation_event (
    id UUID PRIMARY KEY,
    event_type VARCHAR(50) NOT NULL,
    operation_type VARCHAR(40) NOT NULL,
    operation_id UUID NOT NULL,
    correlation_id UUID NOT NULL,
    case_id UUID,
    resource_id UUID,
    attempt_number INTEGER NOT NULL,
    error_code VARCHAR(60),
    occurred_at TIMESTAMP WITH TIME ZONE NOT NULL,
    recorded_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deduplication_key VARCHAR(250) NOT NULL UNIQUE,
    schema_version INTEGER NOT NULL DEFAULT 1,
    CONSTRAINT fk_technical_operation_case
        FOREIGN KEY (case_id) REFERENCES medical_case(id) ON DELETE SET NULL,
    CONSTRAINT chk_technical_operation_event_type CHECK (event_type IN (
        'EVENT_PUBLISH_FAILED',
        'NOTIFICATION_DELIVERY_FAILED',
        'NOTIFICATION_RETRY_EXHAUSTED',
        'ASYNC_CLEANUP_FAILED',
        'ASYNC_CLEANUP_COMPLETED'
    )),
    CONSTRAINT chk_technical_operation_type CHECK (operation_type IN (
        'OUTBOX_MATERIALIZATION',
        'NOTIFICATION_EMAIL_DELIVERY',
        'INPUT_FILE_CLEANUP'
    )),
    CONSTRAINT chk_technical_operation_attempt CHECK (attempt_number > 0),
    CONSTRAINT chk_technical_operation_error_code CHECK (
        error_code IS NULL OR error_code IN (
            'OUTBOX_PROCESSING_ERROR',
            'DELIVERY_TEMPORARY_ERROR',
            'DELIVERY_TIMEOUT',
            'DELIVERY_PERMANENT_ERROR',
            'DELIVERY_UNKNOWN_ERROR',
            'STORAGE_DELETE_ERROR',
            'STORAGE_OBJECT_REMAINS'
        )
    ),
    CONSTRAINT chk_technical_operation_payload CHECK (
        (event_type = 'EVENT_PUBLISH_FAILED'
            AND operation_type = 'OUTBOX_MATERIALIZATION' AND error_code IS NOT NULL
            AND error_code = 'OUTBOX_PROCESSING_ERROR')
        OR (event_type = 'NOTIFICATION_DELIVERY_FAILED'
            AND operation_type = 'NOTIFICATION_EMAIL_DELIVERY'
            AND error_code IS NOT NULL
            AND error_code IN ('DELIVERY_TEMPORARY_ERROR', 'DELIVERY_TIMEOUT',
                'DELIVERY_PERMANENT_ERROR', 'DELIVERY_UNKNOWN_ERROR'))
        OR (event_type = 'NOTIFICATION_RETRY_EXHAUSTED'
            AND operation_type = 'NOTIFICATION_EMAIL_DELIVERY'
            AND error_code IS NOT NULL
            AND error_code IN ('DELIVERY_TEMPORARY_ERROR', 'DELIVERY_TIMEOUT',
                'DELIVERY_PERMANENT_ERROR', 'DELIVERY_UNKNOWN_ERROR'))
        OR (event_type = 'ASYNC_CLEANUP_FAILED'
            AND operation_type = 'INPUT_FILE_CLEANUP'
            AND error_code IS NOT NULL
            AND error_code IN ('STORAGE_DELETE_ERROR', 'STORAGE_OBJECT_REMAINS'))
        OR (event_type = 'ASYNC_CLEANUP_COMPLETED'
            AND operation_type = 'INPUT_FILE_CLEANUP' AND error_code IS NULL)
    ),
    CONSTRAINT chk_technical_operation_schema_version CHECK (schema_version > 0)
);

CREATE INDEX idx_technical_operation_occurred
    ON technical_operation_event(occurred_at DESC, id DESC);

CREATE INDEX idx_technical_operation_operation
    ON technical_operation_event(operation_type, operation_id, attempt_number);

CREATE INDEX idx_technical_operation_case_occurred
    ON technical_operation_event(case_id, occurred_at DESC, id DESC)
    WHERE case_id IS NOT NULL;

ALTER TABLE notification_outbox
    DROP CONSTRAINT chk_notification_outbox_status;
ALTER TABLE notification_outbox
    ADD CONSTRAINT chk_notification_outbox_status
        CHECK (status IN ('PENDING', 'PROCESSING', 'COMPLETED', 'FAILED'));

ALTER TABLE notification_outbox
    ADD COLUMN failed_at TIMESTAMP WITH TIME ZONE,
    ADD COLUMN last_error_code VARCHAR(60);

ALTER TABLE notification_outbox
    ADD CONSTRAINT chk_notification_outbox_failure_state CHECK (
        (status = 'FAILED' AND failed_at IS NOT NULL AND last_error_code IS NOT NULL
            AND last_error_code = 'OUTBOX_PROCESSING_ERROR')
        OR (status <> 'FAILED' AND failed_at IS NULL AND last_error_code IS NULL)
    );

ALTER TABLE input_change_file_cleanup
    ADD COLUMN last_error_code VARCHAR(60);

ALTER TABLE input_change_file_cleanup
    ADD CONSTRAINT chk_input_change_cleanup_error_code CHECK (
        last_error_code IS NULL OR last_error_code IN ('STORAGE_DELETE_ERROR', 'STORAGE_OBJECT_REMAINS')
    );
