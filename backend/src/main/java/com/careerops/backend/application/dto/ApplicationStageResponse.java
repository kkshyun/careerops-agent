package com.careerops.backend.application.dto;

import com.careerops.backend.application.ApplicationStage;
import com.careerops.backend.application.StageResult;
import com.careerops.backend.application.StageType;

import java.time.Instant;
import java.time.LocalDateTime;

public record ApplicationStageResponse(
        Long id,
        StageType stageType,
        String label,
        Integer sortOrder,
        LocalDateTime scheduledAt,
        StageResult result,
        String memo,
        Instant createdAt,
        Instant updatedAt
) {
    public static ApplicationStageResponse from(ApplicationStage entity) {
        return new ApplicationStageResponse(
                entity.getId(), entity.getStageType(), entity.getLabel(), entity.getSortOrder(),
                entity.getScheduledAt(), entity.getResult(), entity.getMemo(),
                entity.getCreatedAt(), entity.getUpdatedAt());
    }
}
