package com.careerops.backend.career.dto;

import com.careerops.backend.career.CareerExperience;
import com.careerops.backend.career.ExperienceType;
import java.time.Instant;
import java.time.LocalDate;

public record CareerExperienceResponse(Long id, ExperienceType type, String title, String organization,
                                       String role, LocalDate startDate, LocalDate endDate, String summary,
                                       Instant createdAt, Instant updatedAt) {
    public static CareerExperienceResponse from(CareerExperience entity) {
        return new CareerExperienceResponse(entity.getId(), entity.getType(), entity.getTitle(),
                entity.getOrganization(), entity.getRole(), entity.getStartDate(), entity.getEndDate(),
                entity.getSummary(), entity.getCreatedAt(), entity.getUpdatedAt());
    }
}
