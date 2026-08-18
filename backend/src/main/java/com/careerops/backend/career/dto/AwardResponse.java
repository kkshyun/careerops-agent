package com.careerops.backend.career.dto;

import com.careerops.backend.career.Award;
import java.time.Instant;
import java.time.LocalDate;

public record AwardResponse(Long id, String title, String issuer, LocalDate awardedDate,
                            String description, Instant createdAt, Instant updatedAt) {
    public static AwardResponse from(Award entity) {
        return new AwardResponse(entity.getId(), entity.getTitle(), entity.getIssuer(), entity.getAwardedDate(),
                entity.getDescription(), entity.getCreatedAt(), entity.getUpdatedAt());
    }
}
