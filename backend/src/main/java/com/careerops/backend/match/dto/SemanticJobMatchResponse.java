package com.careerops.backend.match.dto;

import java.time.Instant;
import java.util.List;

/** semanticScore는 이 API의 대표 관련도이며 합격 가능성이 아니다. LLM 특성상 동일 입력의 완전한 결정성을 보장하지 않는다. */
public record SemanticJobMatchResponse(
        Long jobPostingId,
        double deterministicScore,
        double semanticScore,
        List<SemanticMatchEvidence> experienceMatches,
        List<SemanticMatchEvidence> certificationMatches,
        List<SemanticMatchEvidence> educationMatches,
        List<SemanticMatchEvidence> awardMatches,
        List<String> gaps,
        Instant computedAt) {
}
