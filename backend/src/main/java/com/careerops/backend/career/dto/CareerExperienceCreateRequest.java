package com.careerops.backend.career.dto;

import com.careerops.backend.career.ExperienceType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;

public record CareerExperienceCreateRequest(
        @NotNull ExperienceType type,
        @NotBlank @Size(max = 200) String title,
        @Size(max = 200) String organization,
        @Size(max = 200) String role,
        LocalDate startDate,
        LocalDate endDate,
        @Size(max = 500) String summary,
        @Size(max = 4000) String detail,
        List<@Valid ExperienceBulletRequest> bullets,
        List<@NotBlank @Size(max = 50) String> tags) {}
