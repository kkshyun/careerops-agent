package com.careerops.backend.career.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

public record CertificationCreateRequest(
        @NotBlank @Size(max = 200) String name,
        @Size(max = 200) String issuer,
        LocalDate acquiredDate,
        LocalDate expirationDate,
        @Size(max = 100) String credentialId,
        @Size(max = 2000) String description) {}
