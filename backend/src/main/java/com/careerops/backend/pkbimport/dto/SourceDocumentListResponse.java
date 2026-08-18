package com.careerops.backend.pkbimport.dto;

import com.careerops.backend.pkbimport.SourceDocument;
import org.springframework.data.domain.Page;

import java.util.List;

public record SourceDocumentListResponse(List<SourceDocumentResponse> content, long totalElements,
                                         int totalPages, int page, int size) {
    public static SourceDocumentListResponse from(Page<SourceDocument> page) {
        return new SourceDocumentListResponse(
                page.getContent().stream().map(SourceDocumentResponse::from).toList(),
                page.getTotalElements(), page.getTotalPages(), page.getNumber(), page.getSize());
    }
}
