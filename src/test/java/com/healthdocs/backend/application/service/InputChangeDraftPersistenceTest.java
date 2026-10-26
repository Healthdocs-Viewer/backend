package com.healthdocs.backend.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.healthdocs.backend.adapter.out.persistence.InputChangeRequestRepository;
import com.healthdocs.backend.adapter.out.persistence.entity.AppUserEntity;
import com.healthdocs.backend.adapter.out.persistence.entity.InputChangeRequestEntity;
import com.healthdocs.backend.adapter.out.persistence.entity.MedicalCaseEntity;
import com.healthdocs.backend.adapter.out.persistence.entity.StoredFileEntity;
import com.healthdocs.backend.domain.model.CaseStatus;
import com.healthdocs.backend.domain.model.FileKind;
import com.healthdocs.backend.domain.model.Role;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.ANY)
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create"
})
class InputChangeDraftPersistenceTest {
    @Autowired private TestEntityManager entityManager;
    @Autowired private InputChangeRequestRepository requests;

    @Test
    void persistsOriginalAndProposedMembershipsAndKeepsRemovalHistory() {
        AppUserEntity owner = entityManager.persistAndFlush(new AppUserEntity(
                "owner-" + UUID.randomUUID(), "owner-" + UUID.randomUUID() + "@test.local", Role.PATIENT));
        MedicalCaseEntity medicalCase = entityManager.persistAndFlush(new MedicalCaseEntity(
                UUID.randomUUID(), owner, CaseStatus.FILES_RECEIVED,
                "CAS-" + UUID.randomUUID().toString().substring(0, 8), "Estudio"));
        StoredFileEntity original = new StoredFileEntity(UUID.randomUUID(), medicalCase,
                "input/" + UUID.randomUUID(), FileKind.INPUT, "original.pdf", "application/pdf", 12L, owner.getId());
        original.confirm(Instant.parse("2026-10-15T12:00:00Z"));
        entityManager.persistAndFlush(original);

        InputChangeRequestEntity request = new InputChangeRequestEntity(medicalCase, owner,
                CaseStatus.FILES_RECEIVED);
        request.initializeFileSets(List.of(original), owner.getId(), Instant.parse("2026-10-15T12:01:00Z"));
        UUID requestId = requests.saveAndFlush(request).getId();
        assertNotNull(requestId);

        entityManager.clear();
        InputChangeRequestEntity reloaded = requests.findById(requestId).orElseThrow();
        assertEquals(1, reloaded.originalFiles().size());
        assertEquals(1, reloaded.proposedFiles().size());
        reloaded.removeProposedFile(original.getId(), owner.getId(), Instant.parse("2026-10-15T12:02:00Z"));
        entityManager.flush();
        entityManager.clear();

        InputChangeRequestEntity afterRemoval = requests.findById(requestId).orElseThrow();
        assertEquals(1, afterRemoval.originalFiles().size());
        assertEquals(0, afterRemoval.proposedFiles().size());
        assertEquals(2, afterRemoval.getFiles().size());
    }
}
