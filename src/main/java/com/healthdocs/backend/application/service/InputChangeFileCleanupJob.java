package com.healthdocs.backend.application.service;

import com.healthdocs.backend.adapter.out.persistence.InputChangeFileCleanupRepository;
import com.healthdocs.backend.adapter.out.persistence.StoredFileRepository;
import com.healthdocs.backend.adapter.out.persistence.entity.InputChangeFileCleanupEntity;
import com.healthdocs.backend.application.port.out.StoragePort;
import com.healthdocs.backend.application.port.out.TechnicalOperationEventPort;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Profile("!test")
public class InputChangeFileCleanupJob {
    private static final Logger log = LoggerFactory.getLogger(InputChangeFileCleanupJob.class);
    private static final Duration MAX_RETRY_DELAY = Duration.ofHours(6);

    private final InputChangeFileCleanupRepository cleanup;
    private final StoredFileRepository files;
    private final StoragePort storage;
    private final TechnicalOperationEventPort technicalEvents;
    private final Clock clock;

    public InputChangeFileCleanupJob(InputChangeFileCleanupRepository cleanup,
            StoredFileRepository files, StoragePort storage, TechnicalOperationEventPort technicalEvents,
            Clock clock) {
        this.cleanup = cleanup;
        this.files = files;
        this.storage = storage;
        this.technicalEvents = technicalEvents;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${healthdocs.input-change.cleanup-delay-ms:60000}")
    @Transactional
    public void run() {
        Instant now = clock.instant();
        for (var task : cleanup.findDueForUpdate(now)) {
            var file = files.findById(task.getFileId()).orElse(null);
            if (file == null || file.isInputActive()) {
                task.markSkipped(now);
                continue;
            }
            int attempt = task.getAttempts() + 1;
            var caseId = file.getMedicalCase().getId();
            String failureCode = null;
            try {
                storage.deleteObject(file.getObjectKey());
                if (storage.objectExists(file.getObjectKey())) {
                    failureCode = "STORAGE_OBJECT_REMAINS";
                }
            } catch (RuntimeException error) {
                failureCode = "STORAGE_DELETE_ERROR";
            }
            if (failureCode != null) {
                recordFailure(task, file.getId(), caseId, attempt, failureCode, now);
                log.warn("Input-file cleanup failed for operation {} attempt {} code {}",
                        task.getFileId(), attempt, failureCode);
            } else {
                task.markSucceeded(now);
                technicalEvents.record(new TechnicalOperationEventPort.Event(
                        "ASYNC_CLEANUP_COMPLETED", "INPUT_FILE_CLEANUP", task.getFileId(), task.getFileId(),
                        caseId, task.getFileId(), attempt, null, now,
                        "ASYNC_CLEANUP_COMPLETED:" + task.getFileId() + ":" + attempt));
            }
        }
    }

    private void recordFailure(InputChangeFileCleanupEntity task, UUID fileId, UUID caseId, int attempt,
            String errorCode, Instant now) {
        task.recordFailedAttempt(errorCode, now.plus(retryDelay(attempt)));
        technicalEvents.record(new TechnicalOperationEventPort.Event(
                "ASYNC_CLEANUP_FAILED", "INPUT_FILE_CLEANUP", fileId, fileId, caseId, fileId, attempt,
                errorCode, now, "ASYNC_CLEANUP_FAILED:" + fileId + ":" + attempt));
    }

    static Duration retryDelay(int attempt) {
        long seconds = Math.min(MAX_RETRY_DELAY.toSeconds(), 60L * (1L << Math.min(10, Math.max(0, attempt - 1))));
        return Duration.ofSeconds(seconds);
    }
}
