package com.healthdocs.backend.domain.model;

public enum CaseStatus {
    CONSENT_PENDING,
    FILE_PENDING,
    CONFIRMATION_PENDING,
    FILES_RECEIVED,
    INPUT_CHANGE_DRAFT,
    INPUT_CHANGE_REVIEW,
    ENABLED_FOR_PROCESSING,
    RESULT_UPLOADED,
    RESULT_AVAILABLE;

    public boolean canTransitionTo(CaseStatus next) {
        return switch (this) {
            case CONSENT_PENDING -> next == FILE_PENDING;
            case FILE_PENDING -> next == CONFIRMATION_PENDING;
            case CONFIRMATION_PENDING -> next == FILES_RECEIVED;
            case FILES_RECEIVED -> next == ENABLED_FOR_PROCESSING || next == INPUT_CHANGE_DRAFT;
            case INPUT_CHANGE_DRAFT -> next == INPUT_CHANGE_REVIEW
                    || next == FILES_RECEIVED
                    || next == ENABLED_FOR_PROCESSING;
            case INPUT_CHANGE_REVIEW -> next == FILES_RECEIVED
                    || next == ENABLED_FOR_PROCESSING;
            case ENABLED_FOR_PROCESSING -> next == FILES_RECEIVED || next == RESULT_UPLOADED || next == INPUT_CHANGE_DRAFT;
            case RESULT_UPLOADED -> next == RESULT_AVAILABLE || next == ENABLED_FOR_PROCESSING;
            case RESULT_AVAILABLE -> next == ENABLED_FOR_PROCESSING;
        };
    }
}
