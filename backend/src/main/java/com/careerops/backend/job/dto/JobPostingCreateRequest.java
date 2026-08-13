package com.careerops.backend.job.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.hibernate.validator.constraints.URL;

import java.time.LocalDate;

public record JobPostingCreateRequest(
        @NotBlank @Size(max = 255) String companyName,
        @NotBlank @Size(max = 255) String title,
        @Size(max = 255) String employmentType,
        @Size(max = 255) String jobCategory,
        @Size(max = 255) String location,
        LocalDate applicationStartAt,
        LocalDate applicationEndAt,
        @NotBlank @Size(max = 255) String source,
        @URL @Size(max = 2048) String sourceUrl,
        @Size(max = 255) String externalId
) {
}
