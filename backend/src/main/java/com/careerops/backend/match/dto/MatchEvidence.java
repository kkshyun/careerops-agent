package com.careerops.backend.match.dto;

import java.util.List;

public record MatchEvidence(
        String type,
        Long id,
        String title,
        double score,
        List<String> matchedFields
) {
    public MatchEvidence {
        matchedFields = List.copyOf(matchedFields);
    }
}
