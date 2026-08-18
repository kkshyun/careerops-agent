package com.careerops.backend.pkbimport.dto;

import com.careerops.backend.pkbimport.DocumentType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record SourceDocumentCreateRequest(
        @Size(max = 255) String fileName,
        @NotNull DocumentType documentType,
        @NotBlank @Size(max = 50000) String rawText) {}
