package com.careerops.backend.recommend.dto;

import java.time.LocalDate;

public record RecommendationJobCandidate(Long id, String companyName, String title, String jobCategory,
        String careerLevel, String educationRequirement, LocalDate applicationEndAt) {}
