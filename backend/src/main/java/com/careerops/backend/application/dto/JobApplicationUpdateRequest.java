package com.careerops.backend.application.dto;

import com.careerops.backend.application.ApplicationStatus;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record JobApplicationUpdateRequest(
        ApplicationStatus status,
        @Size(max = 2000) String memo,
        LocalDate appliedAt
) {
}
