package com.careerops.backend.applicationdraft.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

public record QuestionRequest(@NotBlank String questionId, @NotBlank String question, @Positive Integer maxLength) {}
