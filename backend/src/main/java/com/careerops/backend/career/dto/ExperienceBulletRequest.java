package com.careerops.backend.career.dto;

import com.careerops.backend.career.BulletType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.jspecify.annotations.Nullable;

public record ExperienceBulletRequest(@Nullable BulletType bulletType,
                                      @NotBlank @Size(max = 1000) String content) {}
