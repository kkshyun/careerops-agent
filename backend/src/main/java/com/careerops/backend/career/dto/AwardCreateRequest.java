package com.careerops.backend.career.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

public record AwardCreateRequest(
        @NotBlank @Size(max = 200) String title,
        @Size(max = 200) String issuer,
        LocalDate awardedDate,
        @Size(max = 2000) String description) {}
