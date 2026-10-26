-- Persistent user inbox, per-channel delivery state, preferences, and a durable
-- handoff from committed case activity to notification processing.
-- No message body, email address, clinical value, or temporary URL is stored.
CREATE TABLE notification_inbox (
    id UUID PRIMARY KEY,
    recipient_user_id UUID,
    notification_type VARCHAR(50) NOT NULL,
    case_id UUID NOT NULL,
    related_entity_type VARCHAR(30) NOT NULL,
    related_entity_id UUID NOT NULL,
    source_event_key VARCHAR(200) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    read_at TIMESTAMP WITH TIME ZONE,
    schema_version INTEGER NOT NULL DEFAULT 1,
    CONSTRAINT fk_notification_inbox_recipient
        FOREIGN KEY (recipient_user_id) REFERENCES app_user(id) ON DELETE SET NULL,
    CONSTRAINT fk_notification_inbox_case
        FOREIGN KEY (case_id) REFERENCES medical_case(id) ON DELETE CASCADE,
    CONSTRAINT uq_notification_inbox_source_event_recipient UNIQUE (source_event_key, recipient_user_id),
    CONSTRAINT chk_notification_inbox_type CHECK (notification_type IN ('RESULT_AVAILABLE')),
    CONSTRAINT chk_notification_inbox_reference CHECK (
        related_entity_type = 'RESULT' AND related_entity_id IS NOT NULL
    ),
    CONSTRAINT chk_notification_inbox_read_at CHECK (read_at IS NULL OR read_at >= created_at),
    CONSTRAINT chk_notification_inbox_schema_version CHECK (schema_version > 0)
);

CREATE INDEX idx_notification_inbox_recipient_created
    ON notification_inbox(recipient_user_id, created_at DESC, id DESC)
    WHERE recipient_user_id IS NOT NULL;

CREATE INDEX idx_notification_inbox_case_created
    ON notification_inbox(case_id, created_at DESC, id DESC);

CREATE TABLE notification_channel_delivery (
    id UUID PRIMARY KEY,
    notification_id UUID NOT NULL,
    channel VARCHAR(20) NOT NULL,
    status VARCHAR(20) NOT NULL,
    attempt_count INTEGER NOT NULL DEFAULT 0,
    queued_at TIMESTAMP WITH TIME ZONE NOT NULL,
    next_attempt_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    lease_until TIMESTAMP WITH TIME ZONE,
    last_attempt_at TIMESTAMP WITH TIME ZONE,
    sent_at TIMESTAMP WITH TIME ZONE,
    delivered_at TIMESTAMP WITH TIME ZONE,
    failed_at TIMESTAMP WITH TIME ZONE,
    CONSTRAINT fk_notification_delivery_inbox
        FOREIGN KEY (notification_id) REFERENCES notification_inbox(id) ON DELETE CASCADE,
    CONSTRAINT uq_notification_delivery_channel UNIQUE (notification_id, channel),
    CONSTRAINT chk_notification_delivery_channel CHECK (channel IN ('IN_APP', 'EMAIL')),
    CONSTRAINT chk_notification_delivery_status CHECK (
        status IN ('PENDING', 'PROCESSING', 'SENT', 'DELIVERED', 'FAILED', 'SKIPPED')
    ),
    CONSTRAINT chk_notification_delivery_attempt_count CHECK (attempt_count >= 0)
);

CREATE INDEX idx_notification_delivery_pending
    ON notification_channel_delivery(status, next_attempt_at, queued_at)
    WHERE status IN ('PENDING', 'PROCESSING');

CREATE TABLE notification_preference (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    notification_type VARCHAR(50) NOT NULL,
    channel VARCHAR(20) NOT NULL,
    enabled BOOLEAN NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_notification_preference_user
        FOREIGN KEY (user_id) REFERENCES app_user(id) ON DELETE CASCADE,
    CONSTRAINT uq_notification_preference_user_type_channel
        UNIQUE (user_id, notification_type, channel),
    CONSTRAINT chk_notification_preference_type CHECK (notification_type IN ('RESULT_AVAILABLE')),
    CONSTRAINT chk_notification_preference_channel CHECK (channel IN ('IN_APP', 'EMAIL'))
);

CREATE TABLE notification_outbox (
    id UUID PRIMARY KEY,
    event_type VARCHAR(50) NOT NULL,
    source_event_key VARCHAR(200) NOT NULL,
    case_id UUID NOT NULL,
    recipient_user_id UUID,
    published_by_user_id UUID,
    related_entity_type VARCHAR(30) NOT NULL,
    related_entity_id UUID NOT NULL,
    occurred_at TIMESTAMP WITH TIME ZONE NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    attempt_count INTEGER NOT NULL DEFAULT 0,
    next_attempt_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    lease_until TIMESTAMP WITH TIME ZONE,
    processed_at TIMESTAMP WITH TIME ZONE,
    CONSTRAINT fk_notification_outbox_case
        FOREIGN KEY (case_id) REFERENCES medical_case(id) ON DELETE CASCADE,
    CONSTRAINT fk_notification_outbox_recipient
        FOREIGN KEY (recipient_user_id) REFERENCES app_user(id) ON DELETE SET NULL,
    CONSTRAINT fk_notification_outbox_published_by
        FOREIGN KEY (published_by_user_id) REFERENCES app_user(id) ON DELETE SET NULL,
    CONSTRAINT uq_notification_outbox_source_event UNIQUE (source_event_key),
    CONSTRAINT chk_notification_outbox_type CHECK (event_type IN ('RESULT_PUBLISHED')),
    CONSTRAINT chk_notification_outbox_reference CHECK (
        related_entity_type = 'RESULT' AND related_entity_id IS NOT NULL
    ),
    CONSTRAINT chk_notification_outbox_status CHECK (status IN ('PENDING', 'PROCESSING', 'COMPLETED')),
    CONSTRAINT chk_notification_outbox_attempt_count CHECK (attempt_count >= 0),
    CONSTRAINT chk_notification_outbox_processed_at CHECK (
        (status = 'COMPLETED' AND processed_at IS NOT NULL)
        OR (status <> 'COMPLETED' AND processed_at IS NULL)
    )
);

CREATE INDEX idx_notification_outbox_claim
    ON notification_outbox(status, next_attempt_at, created_at)
    WHERE status IN ('PENDING', 'PROCESSING');
