package com.careerops.backend.career.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.jspecify.annotations.Nullable;
import java.time.LocalDate;

public record AwardCreateRequest(
        @NotBlank @Size(max = 200) String title,
        @Size(max = 200) String issuer,
        @Nullable LocalDate awardedDate,
        @Size(max = 2000) String description) {}
