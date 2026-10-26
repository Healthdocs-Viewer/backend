package com.healthdocs.backend.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.healthdocs.backend.adapter.out.storage.SqsNotificationException;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class OperationalRetryPolicyTest {
    @Test
    void notificationBackoffIsExponentialAndBounded() {
        assertEquals(Duration.ofSeconds(30), NotificationOutboxWorker.backoff(1));
        assertEquals(Duration.ofSeconds(60), NotificationOutboxWorker.backoff(2));
        assertEquals(Duration.ofSeconds(480), NotificationOutboxWorker.backoff(5));
        assertEquals(Duration.ofHours(1), NotificationOutboxWorker.backoff(20));
    }

    @Test
    void cleanupBackoffIsExponentialAndBounded() {
        assertEquals(Duration.ofMinutes(1), InputChangeFileCleanupJob.retryDelay(1));
        assertEquals(Duration.ofMinutes(2), InputChangeFileCleanupJob.retryDelay(2));
        assertEquals(Duration.ofHours(6), InputChangeFileCleanupJob.retryDelay(20));
    }

    @Test
    void publisherErrorsMapToClosedTelemetryCodes() {
        assertEquals("DELIVERY_TIMEOUT", NotificationOutboxWorker.deliveryErrorCode(
                new SqsNotificationException(SqsNotificationException.Kind.TIMEOUT, "ignored")));
        assertEquals("DELIVERY_TEMPORARY_ERROR", NotificationOutboxWorker.deliveryErrorCode(
                new SqsNotificationException(SqsNotificationException.Kind.TEMPORARY, "ignored")));
        assertEquals("DELIVERY_PERMANENT_ERROR", NotificationOutboxWorker.deliveryErrorCode(
                new SqsNotificationException(SqsNotificationException.Kind.CONFIGURATION, "ignored")));
        assertEquals("DELIVERY_UNKNOWN_ERROR", NotificationOutboxWorker.deliveryErrorCode(
                new IllegalStateException("ignored")));
    }

    @Test
    void emailRetriesTemporaryAndTimeoutFailuresUpToFiveAttempts() {
        RuntimeException temporary = new SqsNotificationException(
                SqsNotificationException.Kind.TEMPORARY, "ignored");
        RuntimeException timeout = new SqsNotificationException(
                SqsNotificationException.Kind.TIMEOUT, "ignored");
        RuntimeException permanent = new SqsNotificationException(
                SqsNotificationException.Kind.PERMANENT, "ignored");
        RuntimeException configuration = new SqsNotificationException(
                SqsNotificationException.Kind.CONFIGURATION, "ignored");
        RuntimeException serialization = new SqsNotificationException(
                SqsNotificationException.Kind.SERIALIZATION, "ignored");

        assertTrue(NotificationOutboxWorker.shouldRetryEmailFailure(temporary, 1));
        assertTrue(NotificationOutboxWorker.shouldRetryEmailFailure(timeout, 4));
        assertFalse(NotificationOutboxWorker.shouldRetryEmailFailure(temporary, 5));
        assertFalse(NotificationOutboxWorker.shouldRetryEmailFailure(permanent, 1));
        assertFalse(NotificationOutboxWorker.shouldRetryEmailFailure(configuration, 1));
        assertFalse(NotificationOutboxWorker.shouldRetryEmailFailure(serialization, 1));
        assertTrue(NotificationOutboxWorker.shouldRetryEmailFailure(new IllegalStateException("ignored"), 1));
    }
}
