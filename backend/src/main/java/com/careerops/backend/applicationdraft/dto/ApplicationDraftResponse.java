package com.careerops.backend.applicationdraft.dto;

import java.time.Instant;
import java.util.List;

public record ApplicationDraftResponse(Long jobPostingId, List<QuestionDraftResult> questions,
        OverallStrategy overallStrategy, Instant computedAt) {}
