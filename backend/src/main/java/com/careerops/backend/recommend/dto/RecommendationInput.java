package com.careerops.backend.recommend.dto;

import java.util.List;

public record RecommendationInput(
        List<RecommendationJobCandidate> candidates,
        List<RecommendationExperience> experiences,
        List<RecommendationCertification> certifications,
        List<RecommendationEducation> educations,
        List<RecommendationAward> awards) {}
