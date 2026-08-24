package com.careerops.backend.recommend.dto;

import java.time.LocalDate;
import java.util.List;

public record JobRecommendation(Long jobId, String companyName, String title, LocalDate applicationEndAt,
        /** Relative priority within this candidate batch; not a probability of employment success. */
        Double recommendationScore, String reason, List<Long> careerExperienceIds,
        List<Long> certificationIds, List<Long> educationIds, List<Long> awardIds) {}
