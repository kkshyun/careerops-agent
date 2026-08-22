package com.careerops.backend.agent.llm.dto;

import com.careerops.backend.agent.dto.AgentEvidenceSource;
import java.util.List;

public record RawPkbRecommendation(Long id, String reason, List<AgentEvidenceSource> evidence) {
}
