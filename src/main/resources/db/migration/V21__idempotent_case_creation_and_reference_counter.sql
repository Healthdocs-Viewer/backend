CREATE TABLE case_creation_idempotency (
    id UUID PRIMARY KEY,
    patient_id UUID NOT NULL REFERENCES app_user(id),
    idempotency_key UUID NOT NULL,
    request_hash CHAR(64) NOT NULL,
    case_id UUID NOT NULL UNIQUE REFERENCES medical_case(id) ON DELETE CASCADE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_case_creation_idempotency_patient_key UNIQUE (patient_id, idempotency_key)
);

CREATE TABLE case_reference_lock (
    lock_id INTEGER PRIMARY KEY
);

INSERT INTO case_reference_lock (lock_id) VALUES (1);

CREATE TABLE case_reference_counter (
    reference_date DATE PRIMARY KEY,
    current_value BIGINT NOT NULL CHECK (current_value > 0)
);

