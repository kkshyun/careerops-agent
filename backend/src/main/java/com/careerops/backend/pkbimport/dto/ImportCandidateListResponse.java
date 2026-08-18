package com.careerops.backend.pkbimport.dto;

import org.springframework.data.domain.Page;
import java.util.List;

public record ImportCandidateListResponse(List<ImportCandidateResponse> content, long totalElements,
                                          int totalPages, int page, int size) {
    public static ImportCandidateListResponse from(Page<com.careerops.backend.pkbimport.ImportCandidate> result) {
        return new ImportCandidateListResponse(result.getContent().stream().map(ImportCandidateResponse::from).toList(),
                result.getTotalElements(), result.getTotalPages(), result.getNumber(), result.getSize());
    }
}
