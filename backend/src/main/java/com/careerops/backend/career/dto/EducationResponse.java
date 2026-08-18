package com.careerops.backend.career.dto;

import com.careerops.backend.career.Education;
import com.careerops.backend.career.EducationDegree;
import com.careerops.backend.career.EducationStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

public record EducationResponse(Long id, String institution, String major, EducationDegree degree,
                                EducationStatus status, LocalDate startDate, LocalDate endDate,
                                BigDecimal gpa, BigDecimal gpaScale, String description,
                                Instant createdAt, Instant updatedAt) {
    public static EducationResponse from(Education entity) {
        return new EducationResponse(entity.getId(), entity.getInstitution(), entity.getMajor(),
                entity.getDegree(), entity.getStatus(), entity.getStartDate(), entity.getEndDate(),
                entity.getGpa(), entity.getGpaScale(), entity.getDescription(), entity.getCreatedAt(),
                entity.getUpdatedAt());
    }
}
