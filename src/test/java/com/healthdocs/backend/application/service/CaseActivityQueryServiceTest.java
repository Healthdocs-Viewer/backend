package com.healthdocs.backend.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.healthdocs.backend.adapter.out.persistence.CaseAssignmentRepository;
import com.healthdocs.backend.adapter.out.persistence.MedicalCaseRepository;
import com.healthdocs.backend.adapter.out.persistence.StoredFileRepository;
import com.healthdocs.backend.application.port.out.ActivityEventQueryPort;
import com.healthdocs.backend.application.port.out.AuthProvider.AuthenticatedUser;
import com.healthdocs.backend.domain.model.Role;
import java.lang.reflect.Proxy;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CaseActivityQueryServiceTest {
    private static final Set<String> PATIENT_TYPES = Set.of(
            "CASE_CREATED", "CONSENT_ACCEPTED", "FILE_RECEIVED", "CASE_PROCESSING_ENABLED",
            "RESULT_PUBLISHED", "INPUT_CHANGE_OPENED", "INPUT_CHANGE_SUBMITTED",
            "INPUT_CHANGE_APPROVED", "INPUT_CHANGE_REJECTED", "INPUT_CHANGE_CANCELLED");

    @Test
    void patientMustOwnCaseAndReceivesOnlyPatientVisibleTypes() {
        UUID caseId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        RecordingQuery events = new RecordingQuery();
        var service = service(events, caseId, userId, true, false);
        events.total = 6;
        events.items = List.of(new ActivityEventQueryPort.Event(UUID.randomUUID(), "RESULT_PUBLISHED",
                Instant.parse("2026-10-24T12:00:00Z")));

        var page = service.execute(new AuthenticatedUser(userId, "patient@example.test", Role.PATIENT, true),
                caseId, 0, 5);

        assertEquals(6L, page.totalElements());
        assertEquals(2, page.totalPages());
        assertEquals(1, page.items().size());
        assertEquals(PATIENT_TYPES, events.visibleTypes);
        assertEquals(5, events.limit);
    }

    @Test
    void patientCannotReadAnotherPatientsCase() {
        UUID caseId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        RecordingQuery events = new RecordingQuery();
        var service = service(events, caseId, userId, false, false);

        assertThrows(ForbiddenOperationException.class, () -> service.execute(
                new AuthenticatedUser(userId, "patient@example.test", Role.PATIENT, true), caseId, 0, 20));

        assertEquals(0, events.countCalls);
        assertEquals(0, events.findCalls);
    }

    @Test
    void researcherMustBeAssignedAndInvalidPageSizeIsRejected() {
        UUID caseId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        RecordingQuery events = new RecordingQuery();
        var service = service(events, caseId, userId, false, true);

        var page = service.execute(new AuthenticatedUser(userId, "researcher@example.test", Role.RESEARCHER, true),
                caseId, 0, 20);
        assertEquals(0, page.totalElements());
        assertEquals(null, events.visibleTypes);

        assertThrows(IllegalArgumentException.class, () -> service.execute(
                new AuthenticatedUser(userId, "researcher@example.test", Role.RESEARCHER, true), caseId, 0, 101));
        assertEquals(1, events.findCalls);

        RecordingQuery unauthorizedEvents = new RecordingQuery();
        var unauthorizedService = service(unauthorizedEvents, caseId, userId, false, false);
        assertThrows(ForbiddenOperationException.class, () -> unauthorizedService.execute(
                new AuthenticatedUser(userId, "researcher@example.test", Role.RESEARCHER, true), caseId, 0, 20));
        assertEquals(0, unauthorizedEvents.countCalls);
        assertEquals(0, unauthorizedEvents.findCalls);
    }

    private static CaseActivityQueryService service(RecordingQuery events, UUID caseId, UUID userId,
            boolean patientOwnsCase, boolean researcherAssigned) {
        MedicalCaseRepository cases = proxy(MedicalCaseRepository.class, (method, args) ->
                method.equals("existsByIdAndPatientId") && caseId.equals(args[0]) && userId.equals(args[1])
                        && patientOwnsCase);
        CaseAssignmentRepository assignments = proxy(CaseAssignmentRepository.class, (method, args) ->
                method.equals("existsByCaseIdAndUserId") && caseId.equals(args[0]) && userId.equals(args[1])
                        && researcherAssigned);
        StoredFileRepository files = proxy(StoredFileRepository.class, (method, args) -> false);
        ResourceAuthorization authorization = new ResourceAuthorization(cases, assignments, files);
        return new CaseActivityQueryService(events, authorization);
    }

    private static <T> T proxy(Class<T> type, BooleanMethod handler) {
        Object instance = Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] { type }, (proxy, method, args) -> {
            if (method.getDeclaringClass() == Object.class) {
                return switch (method.getName()) {
                    case "toString" -> type.getSimpleName() + " test proxy";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> null;
                };
            }
            if (method.getReturnType() == boolean.class || method.getReturnType() == Boolean.class) {
                return handler.apply(method.getName(), args == null ? new Object[0] : args);
            }
            if (method.getReturnType() == long.class || method.getReturnType() == Long.class) return 0L;
            if (List.class.isAssignableFrom(method.getReturnType())) return List.of();
            return null;
        });
        return type.cast(instance);
    }

    @FunctionalInterface
    private interface BooleanMethod {
        boolean apply(String method, Object[] args);
    }

    private static final class RecordingQuery implements ActivityEventQueryPort {
        private Set<String> visibleTypes;
        private int limit;
        private int countCalls;
        private int findCalls;
        private long total;
        private List<Event> items = List.of();

        @Override
        public List<Event> findByCase(UUID caseId, Set<String> visibleTypes, int limit, long offset) {
            this.visibleTypes = visibleTypes;
            this.limit = limit;
            findCalls++;
            return items;
        }

        @Override
        public long countByCase(UUID caseId, Set<String> visibleTypes) {
            this.visibleTypes = visibleTypes;
            countCalls++;
            return total;
        }

        @Override
        public List<PatientEvent> findRecentForPatient(UUID patientId, Set<String> visibleTypes, int limit) {
            return List.of();
        }
    }
}
