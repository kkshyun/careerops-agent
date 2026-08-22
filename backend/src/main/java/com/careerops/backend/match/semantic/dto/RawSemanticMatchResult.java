package com.careerops.backend.match.semantic.dto;

import java.util.List;

public record RawSemanticMatchResult(
        double semanticScore,
        List<RawExperienceMatch> experienceMatches,
        List<RawCertificationMatch> certificationMatches,
        List<RawEducationMatch> educationMatches,
        List<RawAwardMatch> awardMatches,
        List<String> gaps) {
}
