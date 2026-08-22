package com.careerops.backend.career.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.jspecify.annotations.Nullable;
import java.time.LocalDate;

public record CertificationCreateRequest(
        @NotBlank @Size(max = 200) String name,
        @Size(max = 200) String issuer,
        @Nullable LocalDate acquiredDate,
        @Nullable LocalDate expirationDate,
        @Size(max = 100) String credentialId,
        @Size(max = 2000) String description) {}
