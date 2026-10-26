ALTER TABLE technical_operation_event
    DROP CONSTRAINT chk_technical_operation_event_type;
ALTER TABLE technical_operation_event
    ADD CONSTRAINT chk_technical_operation_event_type CHECK (event_type IN (
        'EVENT_PUBLISH_FAILED',
        'NOTIFICATION_DELIVERY_ACCEPTED',
        'NOTIFICATION_DELIVERY_FAILED',
        'NOTIFICATION_RETRY_EXHAUSTED',
        'ASYNC_CLEANUP_FAILED',
        'ASYNC_CLEANUP_COMPLETED'
    ));

ALTER TABLE technical_operation_event
    DROP CONSTRAINT chk_technical_operation_payload;
ALTER TABLE technical_operation_event
    ADD CONSTRAINT chk_technical_operation_payload CHECK (
        (event_type = 'EVENT_PUBLISH_FAILED'
            AND operation_type = 'OUTBOX_MATERIALIZATION' AND error_code IS NOT NULL
            AND error_code = 'OUTBOX_PROCESSING_ERROR')
        OR (event_type = 'NOTIFICATION_DELIVERY_ACCEPTED'
            AND operation_type = 'NOTIFICATION_EMAIL_DELIVERY' AND error_code IS NULL)
        OR (event_type = 'NOTIFICATION_DELIVERY_FAILED'
            AND operation_type = 'NOTIFICATION_EMAIL_DELIVERY' AND error_code IS NOT NULL
            AND error_code IN ('DELIVERY_TEMPORARY_ERROR', 'DELIVERY_TIMEOUT',
                'DELIVERY_PERMANENT_ERROR', 'DELIVERY_UNKNOWN_ERROR'))
        OR (event_type = 'NOTIFICATION_RETRY_EXHAUSTED'
            AND operation_type = 'NOTIFICATION_EMAIL_DELIVERY' AND error_code IS NOT NULL
            AND error_code IN ('DELIVERY_TEMPORARY_ERROR', 'DELIVERY_TIMEOUT',
                'DELIVERY_PERMANENT_ERROR', 'DELIVERY_UNKNOWN_ERROR'))
        OR (event_type = 'ASYNC_CLEANUP_FAILED'
            AND operation_type = 'INPUT_FILE_CLEANUP' AND error_code IS NOT NULL
            AND error_code IN ('STORAGE_DELETE_ERROR', 'STORAGE_OBJECT_REMAINS'))
        OR (event_type = 'ASYNC_CLEANUP_COMPLETED'
            AND operation_type = 'INPUT_FILE_CLEANUP' AND error_code IS NULL)
    );
