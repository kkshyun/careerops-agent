package com.careerops.backend.career.dto;

import org.springframework.data.domain.Page;
import java.util.List;

public record CareerExperienceListResponse(List<CareerExperienceResponse> content, long totalElements,
                                           int totalPages, int page, int size) {
    public static CareerExperienceListResponse from(Page<CareerExperienceResponse> page) {
        return new CareerExperienceListResponse(page.getContent(), page.getTotalElements(), page.getTotalPages(),
                page.getNumber(), page.getSize());
    }
}
