-- Restricted audit trail for account changes and authorized sensitive-resource access.
-- This is deliberately separate from case activity, notifications, and operations telemetry.
CREATE TABLE security_audit_event (
    id UUID PRIMARY KEY,
    type VARCHAR(60) NOT NULL,
    occurred_at TIMESTAMP WITH TIME ZONE NOT NULL,
    recorded_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    actor_user_id UUID,
    subject_user_id UUID,
    case_id UUID,
    resource_type VARCHAR(30),
    resource_id UUID,
    action VARCHAR(40),
    changed_field VARCHAR(40),
    previous_value VARCHAR(30),
    new_value VARCHAR(30),
    deduplication_key VARCHAR(200) NOT NULL,
    schema_version INTEGER NOT NULL DEFAULT 1,
    CONSTRAINT fk_security_audit_actor
        FOREIGN KEY (actor_user_id) REFERENCES app_user(id) ON DELETE SET NULL,
    CONSTRAINT fk_security_audit_subject
        FOREIGN KEY (subject_user_id) REFERENCES app_user(id) ON DELETE SET NULL,
    CONSTRAINT fk_security_audit_case
        FOREIGN KEY (case_id) REFERENCES medical_case(id) ON DELETE SET NULL,
    CONSTRAINT uq_security_audit_deduplication_key UNIQUE (deduplication_key),
    CONSTRAINT chk_security_audit_type CHECK (type IN (
        'ACCOUNT_CREATED',
        'ACCOUNT_ENABLED',
        'ACCOUNT_DISABLED',
        'ACCOUNT_ROLE_CHANGED',
        'RESEARCHER_AUTHORIZATION_CHANGED',
        'ACCOUNT_PROFILE_UPDATED',
        'PASSWORD_CHANGED',
        'SENSITIVE_RESOURCE_ACCESS_GRANTED'
    )),
    CONSTRAINT chk_security_audit_schema_version CHECK (schema_version > 0),
    CONSTRAINT chk_security_audit_payload CHECK (
        (type = 'ACCOUNT_ROLE_CHANGED'
            AND previous_value IN ('PATIENT', 'RESEARCHER', 'COORDINATOR', 'ADMIN')
            AND new_value IN ('PATIENT', 'RESEARCHER', 'COORDINATOR', 'ADMIN')
            AND previous_value <> new_value
            AND changed_field IS NULL AND resource_type IS NULL AND resource_id IS NULL AND action IS NULL)
        OR (type = 'RESEARCHER_AUTHORIZATION_CHANGED'
            AND previous_value IN ('AUTHORIZED', 'RESTRICTED', 'UNSET')
            AND new_value IN ('AUTHORIZED', 'RESTRICTED')
            AND previous_value <> new_value
            AND changed_field IS NULL AND resource_type IS NULL AND resource_id IS NULL AND action IS NULL)
        OR (type = 'ACCOUNT_PROFILE_UPDATED'
            AND changed_field IN ('FULL_NAME', 'RESEARCHER_CODE')
            AND previous_value IS NULL AND new_value IS NULL
            AND resource_type IS NULL AND resource_id IS NULL AND action IS NULL)
        OR (type = 'SENSITIVE_RESOURCE_ACCESS_GRANTED'
            AND case_id IS NOT NULL
            AND resource_type IN ('INPUT_FILE', 'RESULT')
            AND resource_id IS NOT NULL
            AND action IN ('DOWNLOAD_URL_ISSUED', 'PREVIEW_URL_ISSUED')
            AND changed_field IS NULL AND previous_value IS NULL AND new_value IS NULL)
        OR (type IN ('ACCOUNT_CREATED', 'ACCOUNT_ENABLED', 'ACCOUNT_DISABLED', 'PASSWORD_CHANGED')
            AND resource_type IS NULL AND resource_id IS NULL AND action IS NULL
            AND changed_field IS NULL AND previous_value IS NULL AND new_value IS NULL)
    )
);

CREATE INDEX idx_security_audit_subject_occurred
    ON security_audit_event(subject_user_id, occurred_at DESC, id DESC)
    WHERE subject_user_id IS NOT NULL;

CREATE INDEX idx_security_audit_actor_occurred
    ON security_audit_event(actor_user_id, occurred_at DESC, id DESC)
    WHERE actor_user_id IS NOT NULL;

CREATE INDEX idx_security_audit_case_occurred
    ON security_audit_event(case_id, occurred_at DESC, id DESC)
    WHERE case_id IS NOT NULL;
