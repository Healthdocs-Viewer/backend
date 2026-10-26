package com.healthdocs.backend.application.service;

import com.healthdocs.backend.application.port.out.CaseRepositoryPort;
import com.healthdocs.backend.domain.model.MedicalCase;
import com.healthdocs.backend.domain.model.Role;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.UUID;
import java.util.List;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CreateCaseUseCase {

    private static final DateTimeFormatter REFERENCE_DATE = DateTimeFormatter.BASIC_ISO_DATE;
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("America/Bogota");

    private final CaseRepositoryPort cases;
    private final Clock clock;
    private com.healthdocs.backend.application.port.out.ActivityEventPort activityEvents;

    public CreateCaseUseCase(CaseRepositoryPort cases, Clock clock) {
        this.cases = cases;
        this.clock = clock;
    }

    @Autowired
    public void setActivityEventRecorder(
            com.healthdocs.backend.application.port.out.ActivityEventPort activityEvents) {
        this.activityEvents = activityEvents;
    }

    @Transactional
    public MedicalCase execute(Command command) {
        return executeIdempotent(command).medicalCase();
    }

    @Transactional
    public CreationResult executeIdempotent(Command command) {
        requireEligibleOwner(command);
        String studyName = requireText(command.studyName(), "studyName", 200);
        String originInstitution = requireText(command.originInstitution(), "originInstitution", 200);
        LocalDate studyDate = command.studyDate();
        if (studyDate == null || studyDate.isAfter(LocalDate.ofInstant(clock.instant(), BUSINESS_ZONE)))
            throw new IllegalArgumentException("studyDate must be a valid date no later than today");
        String additionalReference = optionalText(command.additionalReference(), "additionalReference", 100);
        String notes = optionalText(command.notes(), "notes", 500);
        if (command.idempotencyKey() == null) {
            throw new IllegalArgumentException("Idempotency-Key header is required");
        }
        String requestHash = requestHash(studyName, additionalReference, originInstitution, studyDate, notes);

        // Serializing creation per owner makes the lookup-and-create sequence safe
        // when two requests with the same key arrive at the same time.
        cases.lockCaseCreation(command.patientId());
        var previousRequest = cases.findCaseCreationRequest(command.patientId(), command.idempotencyKey());
        if (previousRequest.isPresent()) {
            if (!previousRequest.get().requestHash().equals(requestHash)) {
                throw new BusinessRuleViolationException("Idempotency-Key was already used with a different request");
            }
            MedicalCase existing = cases.findCaseById(previousRequest.get().caseId())
                    .orElseThrow(() -> new ResourceNotFoundException("Idempotent case not found"));
            return new CreationResult(existing, true);
        }

        Instant now = clock.instant();
        LocalDate today = LocalDate.ofInstant(now, ZoneOffset.UTC);
        String referenceCode = "CAS-" + REFERENCE_DATE.format(today) + "-"
                + String.format("%04d", cases.nextCaseReferenceNumber(today));
        MedicalCase medicalCase = MedicalCase.create(UUID.randomUUID(), command.patientId(), referenceCode,
                studyName, additionalReference, originInstitution, studyDate, notes, now);
        MedicalCase saved = cases.saveCase(medicalCase);
        cases.saveCaseCreationRequest(command.patientId(), command.idempotencyKey(), requestHash, saved.id());
        if (activityEvents != null) {
            activityEvents.record("CASE_CREATED", now, saved.id(), command.patientId(), command.patientId(),
                    "CASE", saved.id(), "CASE_CREATED:" + saved.id());
        }
        return new CreationResult(saved, false);
    }

    private static String requestHash(String studyName, String additionalReference, String originInstitution,
            LocalDate studyDate, String notes) {
        StringBuilder canonical = new StringBuilder();
        for (String value : List.of(studyName, nullableField(additionalReference), originInstitution,
                studyDate.toString(), nullableField(notes))) {
            canonical.append(value.length()).append(':').append(value);
        }
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(canonical.toString().getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }

    private static String nullableField(String value) {
        return value == null ? "" : value;
    }

    private static void requireEligibleOwner(Command command) {
        if (!command.enabled() || command.patientId() == null
                || (command.role() != Role.PATIENT && command.role() != Role.RESEARCHER
                && command.role() != Role.COORDINATOR && command.role() != Role.ADMIN)) {
            throw new ForbiddenOperationException("Only enabled accounts can create cases");
        }
    }

    private static String requireText(String value, String field, int maxLength) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
        String trimmed = value.trim();
        if (trimmed.length() > maxLength) throw new IllegalArgumentException(field + " exceeds maximum length " + maxLength);
        return trimmed;
    }

    private static String optionalText(String value, String field, int maxLength) {
        if (value == null || value.isBlank()) return null;
        String trimmed = value.trim();
        if (trimmed.length() > maxLength) throw new IllegalArgumentException(field + " exceeds maximum length " + maxLength);
        return trimmed;
    }

    public record Command(UUID patientId, Role role, boolean enabled, String studyName, String additionalReference,
            String originInstitution, LocalDate studyDate, String notes, UUID idempotencyKey) {
        public Command(UUID patientId, Role role, boolean enabled, String studyName, String additionalReference,
                String originInstitution, LocalDate studyDate, String notes) {
            this(patientId, role, enabled, studyName, additionalReference, originInstitution, studyDate, notes,
                    UUID.randomUUID());
        }
    }

    public record CreationResult(MedicalCase medicalCase, boolean replayed) { }
}
