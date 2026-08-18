package com.careerops.backend.pkbimport.dto;

import com.careerops.backend.pkbimport.ImportBatch;
import com.careerops.backend.pkbimport.ImportBatchStatus;

import java.time.Instant;

public record ImportBatchResponse(Long id, Long sourceDocumentId, ImportBatchStatus status,
                                  Instant createdAt, Instant completedAt) {
    public static ImportBatchResponse from(ImportBatch entity) {
        return new ImportBatchResponse(entity.getId(), entity.getSourceDocument().getId(), entity.getStatus(),
                entity.getCreatedAt(), entity.getCompletedAt());
    }
}
