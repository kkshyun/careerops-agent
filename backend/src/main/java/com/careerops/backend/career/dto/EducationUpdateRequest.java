package com.careerops.backend.career.dto;

import com.careerops.backend.career.EducationDegree;
import com.careerops.backend.career.EducationStatus;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;

public record EducationUpdateRequest(
        @Size(max = 200) String institution,
        @Size(max = 200) String major,
        EducationDegree degree,
        EducationStatus status,
        LocalDate startDate,
        LocalDate endDate,
        BigDecimal gpa,
        BigDecimal gpaScale,
        @Size(max = 2000) String description) {}
