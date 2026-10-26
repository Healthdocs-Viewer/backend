package com.healthdocs.backend.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.healthdocs.backend.domain.model.CaseStatus;
import com.healthdocs.backend.domain.model.MedicalCase;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class MedicalCaseDomainTest {

    @Test
    void allowsContractStatusTransitions() {
        MedicalCase medicalCase = MedicalCase.create(
                UUID.randomUUID(), UUID.randomUUID(), "CAS-20260915-0001", "Radiografía de tórax", null,
                "Centro de prueba", LocalDate.of(2026, 9, 15), null, Instant.EPOCH);

        medicalCase.consentAccepted(Instant.EPOCH.plusSeconds(1));
        medicalCase.fileUploaded(Instant.EPOCH.plusSeconds(2));
        medicalCase.confirmSubmission(Instant.EPOCH.plusSeconds(3));

        assertEquals(CaseStatus.FILES_RECEIVED, medicalCase.status());
    }

    @Test
    void rejectsInvalidStatusTransition() {
        MedicalCase medicalCase = MedicalCase.create(
                UUID.randomUUID(), UUID.randomUUID(), "CAS-20260915-0001", "Radiografía de tórax", null,
                "Centro de prueba", LocalDate.of(2026, 9, 15), null, Instant.EPOCH);

        assertThrows(
                IllegalStateException.class,
                () -> medicalCase.transitionTo(CaseStatus.RESULT_AVAILABLE, Instant.EPOCH.plusSeconds(1)));
    }
}
