package com.careerops.backend.job.dto;

import com.careerops.backend.job.Attachment;
import com.careerops.backend.job.JobPosting;
import com.careerops.backend.job.RecruitmentStep;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public record JobPostingDetailResponse(
        Long id,
        String companyName,
        String title,
        String employmentType,
        String careerLevel,
        String educationRequirement,
        String status,
        String institutionCode,
        String jobCategory,
        String location,
        LocalDate applicationStartAt,
        LocalDate applicationEndAt,
        String source,
        String sourceUrl,
        String externalId,
        Instant createdAt,
        List<RecruitmentStepResponse> recruitmentSteps,
        List<AttachmentResponse> attachments
) {
    public static JobPostingDetailResponse from(
            JobPosting jobPosting,
            List<RecruitmentStep> recruitmentSteps,
            List<Attachment> attachments) {
        return new JobPostingDetailResponse(
                jobPosting.getId(),
                jobPosting.getCompanyName(),
                jobPosting.getTitle(),
                jobPosting.getEmploymentType(),
                jobPosting.getCareerLevel(),
                jobPosting.getEducationRequirement(),
                jobPosting.getStatus(),
                jobPosting.getInstitutionCode(),
                jobPosting.getJobCategory(),
                jobPosting.getLocation(),
                jobPosting.getApplicationStartAt(),
                jobPosting.getApplicationEndAt(),
                jobPosting.getSource(),
                jobPosting.getSourceUrl(),
                jobPosting.getExternalId(),
                jobPosting.getCreatedAt(),
                recruitmentSteps.stream().map(RecruitmentStepResponse::from).toList(),
                attachments.stream().map(AttachmentResponse::from).toList()
        );
    }
}
