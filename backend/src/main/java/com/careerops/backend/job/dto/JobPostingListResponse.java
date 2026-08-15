package com.careerops.backend.job.dto;

import com.careerops.backend.job.JobPosting;
import org.springframework.data.domain.Page;

import java.util.List;

public record JobPostingListResponse(
        List<JobPostingResponse> content,
        long totalElements,
        int totalPages,
        int page,
        int size
) {
    public static JobPostingListResponse from(Page<JobPosting> page) {
        return new JobPostingListResponse(
                page.getContent().stream().map(JobPostingResponse::from).toList(),
                page.getTotalElements(),
                page.getTotalPages(),
                page.getNumber(),
                page.getSize());
    }
}
