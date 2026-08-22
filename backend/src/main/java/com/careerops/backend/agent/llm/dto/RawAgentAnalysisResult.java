package com.careerops.backend.agent.llm.dto;

import java.util.List;

public record RawAgentAnalysisResult(
        String roleSummary, List<String> keyThemes, List<String> knownRequirements,
        String positioningHeadline, String positioningSummary,
        List<RawExperienceRecommendation> recommendedExperiences,
        List<RawPkbRecommendation> recommendedCertifications,
        List<RawPkbRecommendation> recommendedEducations,
        List<RawPkbRecommendation> recommendedAwards,
        String primaryMessage, List<String> secondaryMessages, List<String> avoidOrBeCareful) {
}
