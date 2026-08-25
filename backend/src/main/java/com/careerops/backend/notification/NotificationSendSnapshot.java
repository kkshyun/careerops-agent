package com.careerops.backend.notification;

import java.time.Instant;
import java.time.LocalDate;

public record NotificationSendSnapshot(Long notificationId, Long jobId, String companyName,
        String title, LocalDate applicationEndAt, String sourceUrl, double recommendationScore,
        String reason, NotificationStatus status, Instant sentAt) {}
