package com.careerops.backend.job.dto;

import com.careerops.backend.job.JobPosting;

import java.time.Instant;
import java.time.LocalDate;

public record JobPostingResponse(
        Long id,
        String companyName,
        String title,
        String employmentType,
        String jobCategory,
        String location,
        LocalDate applicationStartAt,
        LocalDate applicationEndAt,
        String source,
        String sourceUrl,
        String externalId,
        Instant createdAt
) {
    public static JobPostingResponse from(JobPosting jobPosting) {
        return new JobPostingResponse(
                jobPosting.getId(),
                jobPosting.getCompanyName(),
                jobPosting.getTitle(),
                jobPosting.getEmploymentType(),
                jobPosting.getJobCategory(),
                jobPosting.getLocation(),
                jobPosting.getApplicationStartAt(),
                jobPosting.getApplicationEndAt(),
                jobPosting.getSource(),
                jobPosting.getSourceUrl(),
                jobPosting.getExternalId(),
                jobPosting.getCreatedAt()
        );
    }
}
