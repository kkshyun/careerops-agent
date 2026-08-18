package com.careerops.backend.pkbimport.dto;

import com.careerops.backend.pkbimport.CandidateTargetType;

import java.util.List;
import java.util.Map;

public record ExtractionResponse(
        Long batchId,
        int createdCandidateCount,
        Map<CandidateTargetType, Integer> counts,
        List<Long> candidateIds) {}
