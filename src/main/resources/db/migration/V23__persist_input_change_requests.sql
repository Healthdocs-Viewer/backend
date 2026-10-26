-- Composite keys let the database enforce that request owners and files belong
-- to the same case, without changing any existing case or file rows.
ALTER TABLE medical_case
    ADD CONSTRAINT uq_medical_case_id_patient UNIQUE (id, patient_id);

ALTER TABLE stored_file
    ADD CONSTRAINT uq_stored_file_id_case UNIQUE (id, case_id);

CREATE TABLE input_change_request (
    id UUID PRIMARY KEY,
    case_id UUID NOT NULL,
    requested_by UUID NOT NULL,
    status VARCHAR(30) NOT NULL,
    reason VARCHAR(250),
    resume_case_status VARCHAR(50) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    submitted_at TIMESTAMP WITH TIME ZONE,
    decided_at TIMESTAMP WITH TIME ZONE,
    decided_by UUID REFERENCES app_user(id),
    decision_reason VARCHAR(1000),
    cancelled_at TIMESTAMP WITH TIME ZONE,
    cancelled_by UUID REFERENCES app_user(id),
    CONSTRAINT uq_input_change_request_id_case UNIQUE (id, case_id),
    CONSTRAINT fk_input_change_request_owner_case
        FOREIGN KEY (case_id, requested_by) REFERENCES medical_case(id, patient_id),
    CONSTRAINT chk_input_change_request_status
        CHECK (status IN ('DRAFT', 'PENDING_REVIEW', 'APPROVED', 'REJECTED', 'CANCELLED')),
    CONSTRAINT chk_input_change_request_resume_status
        CHECK (resume_case_status IN ('FILES_RECEIVED', 'ENABLED_FOR_PROCESSING')),
    CONSTRAINT chk_input_change_request_audit_state CHECK (
        (status = 'DRAFT'
            AND submitted_at IS NULL AND decided_at IS NULL AND decided_by IS NULL
            AND decision_reason IS NULL AND cancelled_at IS NULL AND cancelled_by IS NULL)
        OR (status = 'PENDING_REVIEW'
            AND reason IS NOT NULL AND length(trim(reason)) > 0
            AND submitted_at IS NOT NULL AND decided_at IS NULL AND decided_by IS NULL
            AND decision_reason IS NULL AND cancelled_at IS NULL AND cancelled_by IS NULL)
        OR (status = 'APPROVED'
            AND reason IS NOT NULL AND length(trim(reason)) > 0
            AND submitted_at IS NOT NULL AND decided_at IS NOT NULL AND decided_by IS NOT NULL
            AND decided_by <> requested_by AND decision_reason IS NULL
            AND cancelled_at IS NULL AND cancelled_by IS NULL)
        OR (status = 'REJECTED'
            AND reason IS NOT NULL AND length(trim(reason)) > 0
            AND submitted_at IS NOT NULL AND decided_at IS NOT NULL AND decided_by IS NOT NULL
            AND decided_by <> requested_by AND decision_reason IS NOT NULL
            AND length(trim(decision_reason)) > 0
            AND cancelled_at IS NULL AND cancelled_by IS NULL)
        OR (status = 'CANCELLED'
            AND submitted_at IS NULL AND decided_at IS NULL AND decided_by IS NULL
            AND decision_reason IS NULL AND cancelled_at IS NOT NULL
            AND cancelled_by = requested_by)
    )
);

-- DRAFT and PENDING_REVIEW are the only active states. Terminal requests remain
-- stored for audit and do not prevent the owner from opening a later request.
CREATE UNIQUE INDEX uq_input_change_request_one_active_per_case
    ON input_change_request(case_id)
    WHERE status IN ('DRAFT', 'PENDING_REVIEW');

CREATE INDEX idx_input_change_request_status_submitted
    ON input_change_request(status, submitted_at);

CREATE TABLE input_change_request_file (
    id UUID PRIMARY KEY,
    request_id UUID NOT NULL,
    case_id UUID NOT NULL,
    file_id UUID NOT NULL,
    file_set VARCHAR(20) NOT NULL,
    added_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    added_by UUID NOT NULL REFERENCES app_user(id),
    removed_at TIMESTAMP WITH TIME ZONE,
    removed_by UUID REFERENCES app_user(id),
    CONSTRAINT fk_input_change_request_file_request
        FOREIGN KEY (request_id, case_id) REFERENCES input_change_request(id, case_id),
    CONSTRAINT fk_input_change_request_file_stored_file
        FOREIGN KEY (file_id, case_id) REFERENCES stored_file(id, case_id),
    CONSTRAINT chk_input_change_request_file_set
        CHECK (file_set IN ('ORIGINAL', 'PROPOSED')),
    CONSTRAINT chk_input_change_request_file_removal
        CHECK (
            (removed_at IS NULL AND removed_by IS NULL)
            OR (removed_at IS NOT NULL AND removed_by IS NOT NULL AND file_set = 'PROPOSED')
        )
);

-- Keep removed proposal memberships as audit rows. Re-adding a file creates a
-- new membership row, while only one active membership per set/file is allowed.
CREATE UNIQUE INDEX uq_input_change_request_file_active_membership
    ON input_change_request_file(request_id, file_set, file_id)
    WHERE removed_at IS NULL;

CREATE INDEX idx_input_change_request_file_active_set
    ON input_change_request_file(request_id, file_set)
    WHERE removed_at IS NULL;
