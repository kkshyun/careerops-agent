package com.careerops.backend.agent.dto;

import java.time.Instant;
import java.util.List;

public record AgentAnalysisResponse(
        Long jobPostingId, String roleSummary, List<String> keyThemes, List<String> knownRequirements,
        String positioningHeadline, String positioningSummary,
        List<ExperienceRecommendation> recommendedExperiences,
        List<PkbRecommendation> recommendedCertifications,
        List<PkbRecommendation> recommendedEducations,
        List<PkbRecommendation> recommendedAwards,
        String primaryMessage, List<String> secondaryMessages, List<String> avoidOrBeCareful,
        List<String> gaps, Instant computedAt) {
}
