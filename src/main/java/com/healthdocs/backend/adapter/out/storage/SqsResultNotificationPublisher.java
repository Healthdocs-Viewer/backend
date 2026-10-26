package com.healthdocs.backend.adapter.out.storage;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.healthdocs.backend.application.port.out.ResultNotificationPublisher;
import com.healthdocs.backend.domain.model.ResultAvailableEvent;
import com.healthdocs.backend.infrastructure.config.SqsNotificationProperties;
import java.net.URI;
import java.time.Instant;
import java.util.Objects;
import java.util.concurrent.TimeoutException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.core.retry.RetryPolicy;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.GetQueueUrlRequest;
import software.amazon.awssdk.services.sqs.model.QueueDoesNotExistException;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;
import software.amazon.awssdk.services.sqs.model.SqsException;

@Component
@ConditionalOnProperty(name = "healthdocs.notifications.sqs.enabled", havingValue = "true")
public class SqsResultNotificationPublisher implements ResultNotificationPublisher {
    private final ObjectMapper objectMapper;
    private final SqsNotificationProperties properties;
    private final SqsClient client;

    public SqsResultNotificationPublisher(ObjectMapper objectMapper, SqsNotificationProperties properties) {
        this.objectMapper = objectMapper;
        this.properties = properties;
        validate(properties);
        ClientOverrideConfiguration override = ClientOverrideConfiguration.builder()
                .apiCallTimeout(properties.timeout())
                .apiCallAttemptTimeout(properties.timeout())
                .retryPolicy(RetryPolicy.none())
                .build();
        var builder = SqsClient.builder()
                .region(Region.of(properties.region()))
                .overrideConfiguration(override);
        if (properties.endpoint() != null && !properties.endpoint().isBlank()) {
            try {
                builder.endpointOverride(URI.create(properties.endpoint()));
            } catch (IllegalArgumentException exception) {
                throw new SqsNotificationException(SqsNotificationException.Kind.CONFIGURATION,
                        "Invalid SQS endpoint", exception);
            }
        }
        this.client = builder.build();
    }

    @Override
    public void publish(ResultAvailableEvent event) {
        Objects.requireNonNull(event, "event");
        String body = serialize(event);

        String queueUrl = queueUrl();
        int attempts = properties.maxRetries() + 1;
        for (int attempt = 1; attempt <= attempts; attempt++) {
            try {
                client.sendMessage(SendMessageRequest.builder()
                        .queueUrl(queueUrl)
                        .messageBody(body)
                        .build());
                return;
            } catch (SdkClientException exception) {
                if (attempt == attempts) {
                    throw new SqsNotificationException(isCredentialError(exception)
                            ? SqsNotificationException.Kind.PERMANENT
                            : isTimeout(exception)
                            ? SqsNotificationException.Kind.TIMEOUT
                            : SqsNotificationException.Kind.TEMPORARY,
                            "Temporary SQS client error after retries", exception);
                }
                delay();
            } catch (SqsException exception) {
                if (!isRetryable(exception) || attempt == attempts) {
                    throw new SqsNotificationException(isRetryable(exception)
                            ? SqsNotificationException.Kind.TEMPORARY
                            : SqsNotificationException.Kind.PERMANENT,
                            "SQS rejected result availability event", exception);
                }
                delay();
            }
        }
        throw new SqsNotificationException(SqsNotificationException.Kind.TEMPORARY,
                "SQS publication did not complete");
    }

    @Override
    public boolean isDeliveryEnabled() {
        return true;
    }

    String serialize(ResultAvailableEvent event) {
        try {
            return objectMapper.writeValueAsString(Message.from(event));
        } catch (JsonProcessingException exception) {
            throw new SqsNotificationException(SqsNotificationException.Kind.SERIALIZATION,
                    "Could not serialize result availability event", exception);
        }
    }

    private String queueUrl() {
        try {
            return client.getQueueUrl(GetQueueUrlRequest.builder().queueName(properties.queueName()).build()).queueUrl();
        } catch (QueueDoesNotExistException exception) {
            throw new SqsNotificationException(SqsNotificationException.Kind.CONFIGURATION,
                    "Configured SQS queue does not exist: " + properties.queueName(), exception);
        } catch (SqsException exception) {
            throw new SqsNotificationException(exception.statusCode() == 401 || exception.statusCode() == 403
                    ? SqsNotificationException.Kind.PERMANENT : SqsNotificationException.Kind.CONFIGURATION,
                    "Could not resolve configured SQS queue: " + properties.queueName(), exception);
        } catch (SdkClientException exception) {
            throw new SqsNotificationException(isCredentialError(exception)
                    ? SqsNotificationException.Kind.PERMANENT : SqsNotificationException.Kind.CONFIGURATION,
                    "Could not resolve configured SQS queue: " + properties.queueName(), exception);
        }
    }

    private void delay() {
        try {
            Thread.sleep(properties.retryDelay().toMillis());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new SqsNotificationException(SqsNotificationException.Kind.TEMPORARY,
                    "SQS retry interrupted", exception);
        }
    }

    private static boolean isRetryable(SqsException exception) {
        int status = exception.statusCode();
        String code = exception.awsErrorDetails() == null ? "" : exception.awsErrorDetails().errorCode();
        return status == 429 || status >= 500 || code.toLowerCase().contains("throttl");
    }

    private static boolean isTimeout(SdkClientException exception) {
        return exception.getCause() instanceof TimeoutException
                || exception.getMessage() != null && exception.getMessage().toLowerCase().contains("timeout");
    }

    private static boolean isCredentialError(SdkClientException exception) {
        String message = exception.getMessage();
        return message != null && (message.toLowerCase().contains("credential")
                || message.toLowerCase().contains("access key"));
    }

    private static void validate(SqsNotificationProperties properties) {
        if (properties.region() == null || properties.region().isBlank()
                || properties.queueName() == null || properties.queueName().isBlank()
                || properties.timeout() == null || properties.timeout().isZero() || properties.timeout().isNegative()
                || properties.maxRetries() < 0 || properties.retryDelay() == null || properties.retryDelay().isNegative()) {
            throw new SqsNotificationException(SqsNotificationException.Kind.CONFIGURATION,
                    "Invalid SQS notification configuration");
        }
    }

    private record Message(int eventVersion, String eventType, String eventId, String resultId,
            String caseId, String patientId, String patientEmail, String availableAt,
            String eventTimestamp, String publishedBy) {
        static Message from(ResultAvailableEvent event) {
            return new Message(event.eventVersion(), event.eventType(), event.eventId().toString(),
                    event.resultId().toString(), event.caseId().toString(), event.patientId().toString(),
                    event.patientEmail(), event.availableAt().toString(), event.eventTimestamp().toString(),
                    event.publishedBy() == null ? null : event.publishedBy().toString());
        }
    }
}
