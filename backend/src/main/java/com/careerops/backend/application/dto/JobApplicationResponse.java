package com.careerops.backend.application.dto;

import com.careerops.backend.application.ApplicationStatus;

import java.time.Instant;
import java.time.LocalDate;

public record JobApplicationResponse(
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
        String jobPostingStatus
) {
}
