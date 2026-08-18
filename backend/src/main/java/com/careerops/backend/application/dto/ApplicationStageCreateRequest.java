package com.careerops.backend.application.dto;

import com.careerops.backend.application.StageResult;
import com.careerops.backend.application.StageType;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;

public record ApplicationStageCreateRequest(
        @NotNull StageType stageType,
        String label,
        Integer sortOrder,
        LocalDateTime scheduledAt,
        StageResult result,
        String memo
) {
}
