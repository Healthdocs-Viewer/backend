package com.healthdocs.backend.application.port.out;

import com.healthdocs.backend.domain.model.ResultAvailableEvent;

public interface ResultNotificationPublisher {
    void publish(ResultAvailableEvent event);

    /** Whether this adapter represents a configured external delivery channel. */
    default boolean isDeliveryEnabled() { return true; }
}
