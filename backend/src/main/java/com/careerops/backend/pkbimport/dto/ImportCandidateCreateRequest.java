package com.careerops.backend.pkbimport.dto;

import com.careerops.backend.pkbimport.CandidateTargetType;
import jakarta.validation.constraints.*;

public record ImportCandidateCreateRequest(
        @NotNull CandidateTargetType targetType,
        @NotBlank @Size(max = 10000) String payload) {}
