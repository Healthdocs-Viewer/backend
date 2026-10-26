package com.healthdocs.backend.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.healthdocs.backend.application.port.out.AuthProvider.AuthenticatedUser;
import com.healthdocs.backend.application.port.out.SecurityAuditEventQueryPort;
import com.healthdocs.backend.domain.model.Role;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class SecurityAuditQueryServiceTest {
    @Test
    void onlyEnabledAdminQueriesAndFiltersArePassedThrough() {
        RecordingQuery query = new RecordingQuery();
        var service = new SecurityAuditQueryService(query);
        UUID actor = UUID.randomUUID(), subject = UUID.randomUUID(), resource = UUID.randomUUID();
        var admin = new AuthenticatedUser(UUID.randomUUID(), "ignored", Role.ADMIN, true);

        var page = service.execute(admin, 2, 5, "SENSITIVE_RESOURCE_ACCESS_GRANTED", actor, subject,
                "RESULT", resource);

        assertEquals(10L, query.offset);
        assertEquals("SENSITIVE_RESOURCE_ACCESS_GRANTED", query.filter.type());
        assertEquals(5, query.limit);
        assertEquals(actor, query.filter.actorUserId());
        assertEquals(subject, query.filter.subjectUserId());
        assertEquals("RESULT", query.filter.resourceType());
        assertEquals(resource, query.filter.resourceId());
        assertEquals(3, page.totalPages());
        assertEquals(5, page.size());
    }

    @Test
    void rejectsNonAdminsDisabledAccountsAndInvalidPagingBeforeQuery() {
        RecordingQuery query = new RecordingQuery();
        var service = new SecurityAuditQueryService(query);
        assertThrows(ForbiddenOperationException.class, () -> service.execute(
                new AuthenticatedUser(UUID.randomUUID(), "", Role.COORDINATOR, true), 0, 20, null, null, null, null, null));
        assertThrows(ForbiddenOperationException.class, () -> service.execute(
                new AuthenticatedUser(UUID.randomUUID(), "", Role.ADMIN, false), 0, 20, null, null, null, null, null));
        assertThrows(IllegalArgumentException.class, () -> service.execute(
                new AuthenticatedUser(UUID.randomUUID(), "", Role.ADMIN, true), 0, 101, null, null, null, null, null));
        assertThrows(IllegalArgumentException.class, () -> service.execute(
                new AuthenticatedUser(UUID.randomUUID(), "", Role.ADMIN, true), -1, 20, null, null, null, null, null));
        assertEquals(0, query.calls);
    }

    private static final class RecordingQuery implements SecurityAuditEventQueryPort {
        private int calls;
        private int limit;
        private long offset;
        private Filter filter;

        @Override
        public Page find(Filter filter, int limit, long offset) {
            calls++;
            this.filter = filter;
            this.limit = limit;
            this.offset = offset;
            return new Page(List.of(), 11);
        }
    }
}
