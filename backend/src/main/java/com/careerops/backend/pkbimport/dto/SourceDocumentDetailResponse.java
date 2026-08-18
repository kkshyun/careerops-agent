package com.careerops.backend.pkbimport.dto;

import com.careerops.backend.pkbimport.DocumentType;
import com.careerops.backend.pkbimport.SourceDocument;

import java.time.Instant;

public record SourceDocumentDetailResponse(Long id, String fileName, DocumentType documentType,
                                           String contentHash, String rawText, Instant createdAt) {
    public static SourceDocumentDetailResponse from(SourceDocument entity) {
        return new SourceDocumentDetailResponse(entity.getId(), entity.getFileName(), entity.getDocumentType(),
                entity.getContentHash(), entity.getRawText(), entity.getCreatedAt());
    }
}
