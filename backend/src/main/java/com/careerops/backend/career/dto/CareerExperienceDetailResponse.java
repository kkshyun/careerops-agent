package com.careerops.backend.career.dto;

import com.careerops.backend.career.CareerExperience;
import com.careerops.backend.career.ExperienceType;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public record CareerExperienceDetailResponse(
        Long id, ExperienceType type, String title, String organization, String role,
        LocalDate startDate, LocalDate endDate, String summary, String detail,
        Instant createdAt, Instant updatedAt,
        List<ExperienceBulletResponse> bullets, List<String> tags) {
    public static CareerExperienceDetailResponse from(CareerExperience entity,
                                                       List<ExperienceBulletResponse> bullets,
                                                       List<String> tags) {
        return new CareerExperienceDetailResponse(entity.getId(), entity.getType(), entity.getTitle(),
                entity.getOrganization(), entity.getRole(), entity.getStartDate(), entity.getEndDate(),
                entity.getSummary(), entity.getDetail(), entity.getCreatedAt(), entity.getUpdatedAt(), bullets, tags);
    }
}
