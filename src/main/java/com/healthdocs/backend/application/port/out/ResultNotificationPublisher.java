package com.healthdocs.backend.application.port.out;

import com.healthdocs.backend.domain.model.ResultAvailableEvent;

public interface ResultNotificationPublisher {
    void publish(ResultAvailableEvent event);
}
