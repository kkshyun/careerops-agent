package com.careerops.backend.match.dto;

import java.time.Instant;
import java.util.List;

public record JobMatchResponse(
        Long jobPostingId,
        /** Relevance between the posting categories and PKB evidence, not eligibility or hiring probability. */
        double overallScore,
        List<MatchEvidence> recommendedExperiences,
        List<MatchEvidence> recommendedCertifications,
        List<MatchEvidence> recommendedEducations,
        List<MatchEvidence> recommendedAwards,
        List<String> unmatchedJobCategories,
        String careerLevel,
        String educationRequirement,
        Instant computedAt
) {
    public JobMatchResponse {
        recommendedExperiences = List.copyOf(recommendedExperiences);
        recommendedCertifications = List.copyOf(recommendedCertifications);
        recommendedEducations = List.copyOf(recommendedEducations);
        recommendedAwards = List.copyOf(recommendedAwards);
        unmatchedJobCategories = List.copyOf(unmatchedJobCategories);
    }
}
