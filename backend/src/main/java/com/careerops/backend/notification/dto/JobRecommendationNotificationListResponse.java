package com.careerops.backend.notification.dto;

import org.springframework.data.domain.Page;

import java.util.List;

public record JobRecommendationNotificationListResponse(
        List<JobRecommendationNotificationResponse> content,
        long totalElements, int totalPages, int page, int size) {
    public static JobRecommendationNotificationListResponse from(Page<JobRecommendationNotificationResponse> page) {
        return new JobRecommendationNotificationListResponse(
                page.getContent(), page.getTotalElements(), page.getTotalPages(), page.getNumber(), page.getSize());
    }
}
