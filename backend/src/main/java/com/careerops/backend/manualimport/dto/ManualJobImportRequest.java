package com.careerops.backend.manualimport.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.hibernate.validator.constraints.URL;

import java.time.LocalDate;

public record ManualJobImportRequest(
        @NotBlank @Size(max = 2048)
        @URL(regexp = "^https?://.+", flags = Pattern.Flag.CASE_INSENSITIVE)
        String sourceUrl,
        @NotBlank @Size(max = 255) String companyName,
        @NotBlank @Size(max = 255) String title,
        @Size(max = 255) String employmentType,
        @Size(max = 255) String jobCategory,
        @Size(max = 255) String location,
        LocalDate applicationStartAt,
        LocalDate applicationEndAt
) {
}
