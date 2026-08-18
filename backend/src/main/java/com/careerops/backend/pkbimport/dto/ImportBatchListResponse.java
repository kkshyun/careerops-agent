package com.careerops.backend.pkbimport.dto;

import com.careerops.backend.pkbimport.ImportBatch;
import org.springframework.data.domain.Page;

import java.util.List;

public record ImportBatchListResponse(List<ImportBatchResponse> content, long totalElements,
                                      int totalPages, int page, int size) {
    public static ImportBatchListResponse from(Page<ImportBatch> page) {
        return new ImportBatchListResponse(page.getContent().stream().map(ImportBatchResponse::from).toList(),
                page.getTotalElements(), page.getTotalPages(), page.getNumber(), page.getSize());
    }
}
