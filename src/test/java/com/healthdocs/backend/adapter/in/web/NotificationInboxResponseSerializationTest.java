package com.healthdocs.backend.adapter.in.web;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.healthdocs.backend.application.port.out.NotificationInboxPort;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import java.util.HashSet;
import org.junit.jupiter.api.Test;

class NotificationInboxResponseSerializationTest {
    @Test
    void responseExposesOnlyFieldsNeededByThePatientInterface() {
        var event = new NotificationInboxPort.Event(UUID.randomUUID(), "RESULT_AVAILABLE", "CAS-0001",
                Instant.parse("2026-10-24T12:00:00Z"), null);
        var mapper = new ObjectMapper().registerModule(new JavaTimeModule());

        Set<String> fields = new HashSet<>();
        mapper.valueToTree(event).fieldNames().forEachRemaining(fields::add);

        assertEquals(Set.of("id", "type", "caseReferenceCode", "createdAt", "readAt"), fields);
    }
}
