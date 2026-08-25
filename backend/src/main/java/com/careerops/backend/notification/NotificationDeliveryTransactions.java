package com.careerops.backend.notification;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.util.Optional;

@Service
public class NotificationDeliveryTransactions {
    private final JobRecommendationNotificationRepository repository;
    public NotificationDeliveryTransactions(JobRecommendationNotificationRepository repository) { this.repository = repository; }
    @Transactional public boolean claim(long id, Instant attemptedAt) { return repository.claimForSending(id, attemptedAt) == 1; }
    @Transactional(readOnly = true) public Optional<NotificationSendSnapshot> snapshot(long id) { return repository.findSnapshotById(id); }
    @Transactional public void sent(long id, Instant sentAt) {
        if (repository.markSent(id, sentAt) != 1) throw new IllegalStateException("Notification is no longer SENDING: " + id);
    }
    @Transactional public void failed(long id, String code) {
        if (repository.markFailed(id, code) != 1) throw new IllegalStateException("Notification is no longer SENDING: " + id);
    }
}
