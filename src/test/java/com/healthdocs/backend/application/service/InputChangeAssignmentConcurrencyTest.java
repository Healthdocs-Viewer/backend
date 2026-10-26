package com.healthdocs.backend.application.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

import com.healthdocs.backend.adapter.out.persistence.*;
import com.healthdocs.backend.adapter.out.persistence.entity.AppUserEntity;
import com.healthdocs.backend.adapter.out.persistence.entity.MedicalCaseEntity;
import com.healthdocs.backend.adapter.out.persistence.entity.StoredFileEntity;
import com.healthdocs.backend.application.port.out.AuthProvider.AuthenticatedUser;
import com.healthdocs.backend.application.port.out.StoragePort;
import com.healthdocs.backend.domain.model.*;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.ANY)
@ActiveProfiles("test")
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@TestPropertySource(properties = {
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create",
        "spring.jpa.open-in-view=false"
})
class InputChangeAssignmentConcurrencyTest {
    @Autowired private MedicalCaseRepository cases;
    @Autowired private StoredFileRepository files;
    @Autowired private AppUserRepository users;
    @Autowired private CaseResultRepository results;
    @Autowired private InputChangeRequestRepository requests;
    @Autowired private InputChangeRequestFileRepository requestFiles;
    @Autowired private InputChangeFileCleanupRepository cleanup;
    @Autowired private CaseAssignmentRepository assignments;
    @Autowired private PlatformTransactionManager transactionManager;

    private final Clock clock = Clock.fixed(Instant.parse("2026-10-15T12:00:00Z"), ZoneOffset.UTC);

    @Test
    void assignmentWinningTheLockAllowsDraftToOpenOnEnabledCase() throws Exception {
        Fixture fixture = fixture();
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        AssignCaseUseCase assignment = new AssignCaseUseCase(cases, files, assignments, users, clock);
        InputChangeDraftService drafts = draftService();
        CountDownLatch assignmentLocked = new CountDownLatch(1);
        CountDownLatch releaseAssignment = new CountDownLatch(1);
        CountDownLatch openStarted = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<?> assignmentFuture = pool.submit(() -> transaction.execute(status -> {
                assignment.execute(fixture.coordinatorActor(), fixture.caseId(), fixture.researcherId());
                assignmentLocked.countDown();
                await(releaseAssignment);
                return null;
            }));
            assertTrue(assignmentLocked.await(5, TimeUnit.SECONDS));
            Future<InputChangeDraftService.RequestView> openFuture = pool.submit(() -> {
                openStarted.countDown();
                return transaction.execute(status -> drafts.open(fixture.ownerActor(), fixture.caseId()));
            });
            assertTrue(openStarted.await(5, TimeUnit.SECONDS));
            releaseAssignment.countDown();
            assignmentFuture.get(10, TimeUnit.SECONDS);
            InputChangeDraftService.RequestView opened = openFuture.get(10, TimeUnit.SECONDS);

            assertEquals(CaseStatus.ENABLED_FOR_PROCESSING, opened.resumeCaseStatus());
            assertEquals(InputChangeRequestStatus.DRAFT, opened.status());
            transaction.executeWithoutResult(status -> {
                assertEquals(CaseStatus.INPUT_CHANGE_DRAFT, cases.findById(fixture.caseId()).orElseThrow().getStatus());
                assertTrue(assignments.existsByCaseIdAndUserId(fixture.caseId(), fixture.researcherId()));
            });
        } finally {
            releaseAssignment.countDown();
            pool.shutdownNow();
        }
    }

    @Test
    void openingDraftWinningTheLockRejectsConcurrentAssignment() throws Exception {
        Fixture fixture = fixture();
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        AssignCaseUseCase assignment = new AssignCaseUseCase(cases, files, assignments, users, clock);
        InputChangeDraftService drafts = draftService();
        CountDownLatch draftLocked = new CountDownLatch(1);
        CountDownLatch releaseDraft = new CountDownLatch(1);
        CountDownLatch assignmentStarted = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<InputChangeDraftService.RequestView> openFuture = pool.submit(() -> transaction.execute(status -> {
                InputChangeDraftService.RequestView opened = drafts.open(fixture.ownerActor(), fixture.caseId());
                draftLocked.countDown();
                await(releaseDraft);
                return opened;
            }));
            assertTrue(draftLocked.await(5, TimeUnit.SECONDS));
            Future<?> assignmentFuture = pool.submit(() -> {
                assignmentStarted.countDown();
                transaction.executeWithoutResult(status -> assignment.execute(
                        fixture.coordinatorActor(), fixture.caseId(), fixture.researcherId()));
            });
            assertTrue(assignmentStarted.await(5, TimeUnit.SECONDS));
            releaseDraft.countDown();
            InputChangeDraftService.RequestView opened = openFuture.get(10, TimeUnit.SECONDS);
            ExecutionException assignmentError = assertThrows(ExecutionException.class,
                    () -> assignmentFuture.get(10, TimeUnit.SECONDS));

            assertEquals(InputChangeRequestStatus.DRAFT, opened.status());
            assertInstanceOf(BusinessRuleViolationException.class, assignmentError.getCause());
            transaction.executeWithoutResult(status -> {
                assertEquals(CaseStatus.INPUT_CHANGE_DRAFT, cases.findById(fixture.caseId()).orElseThrow().getStatus());
                assertFalse(assignments.existsByCaseIdAndUserId(fixture.caseId(), fixture.researcherId()));
            });
        } finally {
            releaseDraft.countDown();
            pool.shutdownNow();
        }
    }

    private InputChangeDraftService draftService() {
        return new InputChangeDraftService(cases, files, users, results, requests, requestFiles, cleanup,
                mock(StoragePort.class), clock);
    }

    private Fixture fixture() {
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        return transaction.execute(status -> {
            String suffix = UUID.randomUUID().toString();
            AppUserEntity owner = users.saveAndFlush(new AppUserEntity(
                    "owner-" + suffix, "owner-" + suffix + "@test.local", Role.PATIENT));
            AppUserEntity coordinator = users.saveAndFlush(new AppUserEntity(
                    "coordinator-" + suffix, "coordinator-" + suffix + "@test.local", Role.COORDINATOR));
            AppUserEntity researcher = users.saveAndFlush(new AppUserEntity(
                    "researcher-" + suffix, "researcher-" + suffix + "@test.local", Role.RESEARCHER));
            UUID caseId = UUID.randomUUID();
            MedicalCaseEntity medicalCase = cases.saveAndFlush(new MedicalCaseEntity(
                    caseId, owner, CaseStatus.FILES_RECEIVED, "CAS-" + suffix.substring(0, 8), "Estudio"));
            StoredFileEntity input = new StoredFileEntity(UUID.randomUUID(), medicalCase, "input/" + suffix,
                    FileKind.INPUT, "original.pdf", "application/pdf", 12L, owner.getId());
            input.confirm(clock.instant());
            files.saveAndFlush(input);
            return new Fixture(owner.getId(), coordinator.getId(), researcher.getId(), caseId);
        });
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(10, TimeUnit.SECONDS)) throw new AssertionError("Timed out waiting for transaction release");
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new AssertionError("Interrupted while waiting for transaction release", interrupted);
        }
    }

    private record Fixture(UUID ownerId, UUID coordinatorId, UUID researcherId, UUID caseId) {
        AuthenticatedUser ownerActor() {
            return new AuthenticatedUser(ownerId, "owner@test.local", Role.PATIENT, true);
        }
        AuthenticatedUser coordinatorActor() {
            return new AuthenticatedUser(coordinatorId, "coordinator@test.local", Role.COORDINATOR, true);
        }
    }
}
