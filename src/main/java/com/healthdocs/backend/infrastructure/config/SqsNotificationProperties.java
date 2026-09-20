package com.healthdocs.backend.infrastructure.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "healthdocs.notifications.sqs")
public record SqsNotificationProperties(
        boolean enabled,
        String region,
        String queueName,
        String endpoint,
        Duration timeout,
        int maxRetries,
        Duration retryDelay) {
}
