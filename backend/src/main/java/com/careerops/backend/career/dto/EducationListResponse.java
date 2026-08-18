package com.careerops.backend.career.dto;

import com.careerops.backend.career.Education;
import org.springframework.data.domain.Page;
import java.util.List;

public record EducationListResponse(List<EducationResponse> content, long totalElements,
                                    int totalPages, int page, int size) {
    public static EducationListResponse from(Page<Education> page) {
        return new EducationListResponse(page.getContent().stream().map(EducationResponse::from).toList(),
                page.getTotalElements(), page.getTotalPages(), page.getNumber(), page.getSize());
    }
}
