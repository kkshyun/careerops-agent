package com.careerops.backend.agent.llm.dto;

import com.careerops.backend.agent.dto.AgentEvidenceSource;
import java.util.List;

public record RawExperienceRecommendation(Long id, String reason, List<String> emphasisPoints,
                                          List<AgentEvidenceSource> evidence) {
}
