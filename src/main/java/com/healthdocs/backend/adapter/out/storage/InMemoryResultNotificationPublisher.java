package com.healthdocs.backend.adapter.out.storage;

import com.healthdocs.backend.application.port.out.ResultNotificationPublisher;
import com.healthdocs.backend.domain.model.ResultAvailableEvent;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.springframework.stereotype.Component;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Component
@ConditionalOnProperty(name = "healthdocs.notifications.sqs.enabled", havingValue = "false", matchIfMissing = true)
public class InMemoryResultNotificationPublisher implements ResultNotificationPublisher {
    private final List<ResultAvailableEvent> events = new CopyOnWriteArrayList<>();
    @Override
    public void publish(ResultAvailableEvent event) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override public void afterCommit() { events.add(event); }
            });
            return;
        }
        events.add(event);
    }
    public List<ResultAvailableEvent> events() { return List.copyOf(events); }
    public int size() { return events.size(); }
    public void clear() { events.clear(); }
    public boolean contains(ResultAvailableEvent event) { return events.contains(event); }
    @Override public boolean isDeliveryEnabled() { return false; }
}
