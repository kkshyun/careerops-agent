package com.careerops.backend.match.dto;

import java.util.List;

/** score는 공고와 PKB 항목의 관련도이며 합격 가능성이 아니다. */
public record SemanticMatchEvidence(String type, Long id, String title, double score,
                                    List<EvidenceSource> evidence, String reason) {
}
