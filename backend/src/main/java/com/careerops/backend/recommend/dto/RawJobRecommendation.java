package com.careerops.backend.recommend.dto;

import java.util.List;

public record RawJobRecommendation(Long jobId, Double recommendationScore, String reason,
        List<Long> careerExperienceIds, List<Long> certificationIds,
        List<Long> educationIds, List<Long> awardIds) {}
