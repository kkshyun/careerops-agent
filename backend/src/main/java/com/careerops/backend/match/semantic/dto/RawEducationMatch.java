package com.careerops.backend.match.semantic.dto;

import com.careerops.backend.match.dto.EvidenceSource;
import java.util.List;

public record RawEducationMatch(Long id, double score, List<EvidenceSource> evidence, String reason) {}
