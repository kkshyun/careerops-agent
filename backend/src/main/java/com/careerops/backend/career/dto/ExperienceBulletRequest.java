package com.careerops.backend.career.dto;

import com.careerops.backend.career.BulletType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ExperienceBulletRequest(BulletType bulletType,
                                      @NotBlank @Size(max = 1000) String content) {}
