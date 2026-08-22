package com.careerops.backend.career.dto;

import com.careerops.backend.career.EducationDegree;
import com.careerops.backend.career.EducationStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.jspecify.annotations.Nullable;
import java.math.BigDecimal;
import java.time.LocalDate;

public record EducationCreateRequest(
        @NotBlank @Size(max = 200) String institution,
        @Size(max = 200) String major,
        @Nullable EducationDegree degree,
        @Nullable EducationStatus status,
        @Nullable LocalDate startDate,
        @Nullable LocalDate endDate,
        @Nullable BigDecimal gpa,
        @Nullable BigDecimal gpaScale,
        @Size(max = 2000) String description) {}
