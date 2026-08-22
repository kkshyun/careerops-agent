package com.careerops.backend.agent.dto;

import java.util.List;

public record ExperienceRecommendation(
        Long id,
        /** DB에서 조회한 실제 제목. */ String title,
        int priority,
        /** MATCH-002가 계산한 원본 score이며 Agent LLM이 생성하지 않는다. */ double semanticMatchScore,
        String reason,
        List<String> emphasisPoints,
        List<AgentEvidenceSource> evidence) {
}
