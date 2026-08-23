package com.careerops.backend.applicationdraft.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;

public record ApplicationDraftRequest(@NotEmpty @Size(max = 10) @Valid List<QuestionRequest> questions) {}
