package com.careerops.backend.recommend.dto;

import java.util.List;

public record RecommendationExperience(Long id, String title, String organization, String role,
        String summary, List<String> tags) {}
