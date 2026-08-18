package com.careerops.backend.pkbimport.dto;

import com.careerops.backend.pkbimport.*;
import java.time.Instant;

public record ImportCandidateResponse(Long id, Long importBatchId, CandidateTargetType targetType,
                                      String payload, ImportCandidateStatus status, Long createdEntityId,
                                      Instant createdAt, Instant reviewedAt) {
    public static ImportCandidateResponse from(ImportCandidate entity) {
        return new ImportCandidateResponse(entity.getId(), entity.getImportBatch().getId(), entity.getTargetType(),
                entity.getPayload(), entity.getStatus(), entity.getCreatedEntityId(), entity.getCreatedAt(),
                entity.getReviewedAt());
    }
}
