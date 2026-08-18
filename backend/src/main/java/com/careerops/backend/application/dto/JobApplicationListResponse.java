package com.careerops.backend.application.dto;

import org.springframework.data.domain.Page;

import java.util.List;

public record JobApplicationListResponse(
        List<JobApplicationResponse> content,
        long totalElements,
        int totalPages,
        int page,
        int size
) {
    public static JobApplicationListResponse from(Page<JobApplicationResponse> page) {
        return new JobApplicationListResponse(
                page.getContent(), page.getTotalElements(), page.getTotalPages(), page.getNumber(), page.getSize());
    }
}
