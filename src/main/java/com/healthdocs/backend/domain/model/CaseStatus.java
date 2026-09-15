package com.healthdocs.backend.domain.model;

public enum CaseStatus {
    CREATED,
    FILES_RECEIVED,
    ENABLED_FOR_PROCESSING,
    RESULT_UPLOADED,
    RESULT_AVAILABLE;

    public boolean canTransitionTo(CaseStatus next) {
        return switch (this) {
            case CREATED -> next == FILES_RECEIVED;
            case FILES_RECEIVED -> next == ENABLED_FOR_PROCESSING;
            case ENABLED_FOR_PROCESSING -> next == RESULT_UPLOADED;
            case RESULT_UPLOADED -> next == RESULT_AVAILABLE;
            case RESULT_AVAILABLE -> false;
        };
    }
}
