package com.careerops.backend.notification.dto;

import com.careerops.backend.notification.NotificationStatus;

import java.time.Instant;
import java.time.LocalDate;

public record JobRecommendationNotificationResponse(
        Long id, Long jobId, String companyName, String title, LocalDate applicationEndAt,
        double recommendationScore, String reason, NotificationStatus status, Instant createdAt) {}
