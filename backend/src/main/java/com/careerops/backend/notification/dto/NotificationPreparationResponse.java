package com.careerops.backend.notification.dto;

import java.util.List;

public record NotificationPreparationResponse(
        int createdCount, int alreadyNotifiedCount,
        List<JobRecommendationNotificationResponse> notifications) {}
