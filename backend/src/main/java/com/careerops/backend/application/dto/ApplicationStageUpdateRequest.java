package com.careerops.backend.application.dto;

import com.careerops.backend.application.StageResult;

import java.time.LocalDateTime;

public record ApplicationStageUpdateRequest(
        String label,
        Integer sortOrder,
        LocalDateTime scheduledAt,
        StageResult result,
        String memo
) {
}
