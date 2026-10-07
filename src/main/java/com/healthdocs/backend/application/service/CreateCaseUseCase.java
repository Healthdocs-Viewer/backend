package com.healthdocs.backend.application.service;

import com.healthdocs.backend.application.port.out.CaseRepositoryPort;
import com.healthdocs.backend.domain.model.MedicalCase;
import com.healthdocs.backend.domain.model.Role;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CreateCaseUseCase {

    private static final DateTimeFormatter REFERENCE_DATE = DateTimeFormatter.BASIC_ISO_DATE;

    private final CaseRepositoryPort cases;
    private final Clock clock;

    public CreateCaseUseCase(CaseRepositoryPort cases, Clock clock) {
        this.cases = cases;
        this.clock = clock;
    }

    @Transactional
    public MedicalCase execute(Command command) {
        requirePatient(command);
        String studyName = requireText(command.studyName(), "studyName");
        Instant now = clock.instant();
        LocalDate today = LocalDate.ofInstant(now, ZoneOffset.UTC);
        String referenceCode = "CAS-" + REFERENCE_DATE.format(today) + "-"
                + String.format("%04d", cases.countCasesCreatedOn(today) + 1);
        MedicalCase medicalCase = MedicalCase.create(
                UUID.randomUUID(), command.patientId(), referenceCode, studyName, now);
        return cases.saveCase(medicalCase);
    }

    private static void requirePatient(Command command) {
        if (!command.enabled() || command.role() != Role.PATIENT) {
            throw new ForbiddenOperationException("Only enabled patients can create cases");
        }
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
        return value.trim();
    }

    public record Command(UUID patientId, Role role, boolean enabled, String studyName) {
    }
}
