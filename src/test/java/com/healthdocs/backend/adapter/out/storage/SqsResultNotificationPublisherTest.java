package com.healthdocs.backend.adapter.out.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.healthdocs.backend.domain.model.ResultAvailableEvent;
import com.healthdocs.backend.infrastructure.config.SqsNotificationProperties;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SqsResultNotificationPublisherTest {
    private static final UUID EVENT_ID = UUID.fromString("2a8f1f48-7f78-4b55-bf8d-9e0d3d0c2b11");
    private static final UUID RESULT_ID = UUID.fromString("3c6d0a7a-3c66-4f84-bf44-14d8b9d5d7a2");
    private static final UUID CASE_ID = UUID.fromString("8ef4b2d3-1e2a-4d6e-9f45-4f7f30cbe111");
    private static final UUID PATIENT_ID = UUID.fromString("9a1f3e42-5d3c-4a20-9c11-6a7f8f90d222");
    private static final UUID PUBLISHED_BY = UUID.fromString("5b1d8a0c-9d3f-4c12-a8e0-7f6c1d4e3333");
    private static final Instant AVAILABLE_AT = Instant.parse("2026-09-19T14:30:00Z");
    private static final Instant EVENT_TIMESTAMP = Instant.parse("2026-09-19T14:30:01Z");

    private SqsResultNotificationPublisher publisher;

    @BeforeEach
    void setUp() {
        publisher = new SqsResultNotificationPublisher(new ObjectMapper(),
                new SqsNotificationProperties(false, "us-east-1", "result-available-dev",
                        "http://localhost:4566", Duration.ofSeconds(1), 0, Duration.ZERO));
    }

    @Test
    void serializesApprovedContractExactly() throws Exception {
        JsonNode json = new ObjectMapper().readTree(publisher.serialize(event()));

        assertEquals(10, json.size());
        assertEquals(1, json.get("eventVersion").asInt());
        assertEquals("RESULT_AVAILABLE", json.get("eventType").asText());
        assertEquals(EVENT_ID.toString(), json.get("eventId").asText());
        assertEquals(RESULT_ID.toString(), json.get("resultId").asText());
        assertEquals(CASE_ID.toString(), json.get("caseId").asText());
        assertEquals(PATIENT_ID.toString(), json.get("patientId").asText());
        assertEquals("paciente@example.com", json.get("patientEmail").asText());
        assertEquals(AVAILABLE_AT.toString(), json.get("availableAt").asText());
        assertEquals(EVENT_TIMESTAMP.toString(), json.get("eventTimestamp").asText());
        assertEquals(PUBLISHED_BY.toString(), json.get("publishedBy").asText());
    }

    @Test
    void doesNotSerializeClinicalOrPrivateFields() throws Exception {
        JsonNode json = new ObjectMapper().readTree(publisher.serialize(event()));

        assertFalse(json.has("diagnosis"));
        assertFalse(json.has("fileName"));
        assertFalse(json.has("downloadUrl"));
        assertFalse(json.has("token"));
        assertFalse(json.has("credentials"));
        assertTrue(json.hasNonNull("eventId"));
    }

    private ResultAvailableEvent event() {
        return new ResultAvailableEvent(1, "RESULT_AVAILABLE", EVENT_ID, RESULT_ID, CASE_ID,
                PATIENT_ID, "paciente@example.com", AVAILABLE_AT, EVENT_TIMESTAMP, PUBLISHED_BY);
    }
}
