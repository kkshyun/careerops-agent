package com.careerops.backend.pkbimport.dto;

import com.careerops.backend.pkbimport.DocumentType;
import com.careerops.backend.pkbimport.SourceDocument;

import java.time.Instant;

public record SourceDocumentResponse(Long id, String fileName, DocumentType documentType,
                                     String contentHash, Instant createdAt) {
    public static SourceDocumentResponse from(SourceDocument entity) {
        return new SourceDocumentResponse(entity.getId(), entity.getFileName(), entity.getDocumentType(),
                entity.getContentHash(), entity.getCreatedAt());
    }
}
