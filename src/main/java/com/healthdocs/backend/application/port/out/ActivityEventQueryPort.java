package com.healthdocs.backend.application.port.out;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public interface ActivityEventQueryPort {
    List<Event> findByCase(UUID caseId, Set<String> visibleTypes, int limit, long offset);
    long countByCase(UUID caseId, Set<String> visibleTypes);
    List<PatientEvent> findRecentForPatient(UUID patientId, Set<String> visibleTypes, int limit);

    record Event(UUID id, String type, Instant occurredAt) { }
    record PatientEvent(UUID id, String type, Instant occurredAt, String caseReferenceCode) { }
}
