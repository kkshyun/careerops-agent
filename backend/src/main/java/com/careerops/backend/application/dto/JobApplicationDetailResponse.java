package com.careerops.backend.application.dto;

import com.careerops.backend.application.ApplicationStatus;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public record JobApplicationDetailResponse(
        Long id,
        ApplicationStatus status,
        String memo,
        LocalDate appliedAt,
        Instant createdAt,
        Instant updatedAt,
        Long jobPostingId,
        String companyName,
        String title,
        LocalDate applicationEndAt,
        String jobPostingStatus,
        List<ApplicationStageResponse> stages
) {
    public static JobApplicationDetailResponse from(
            JobApplicationResponse application, List<ApplicationStageResponse> stages) {
        return new JobApplicationDetailResponse(
                application.id(), application.status(), application.memo(), application.appliedAt(),
                application.createdAt(), application.updatedAt(), application.jobPostingId(),
                application.companyName(), application.title(), application.applicationEndAt(),
                application.jobPostingStatus(), stages);
    }
}
