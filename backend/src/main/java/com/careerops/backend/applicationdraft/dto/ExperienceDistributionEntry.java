package com.careerops.backend.applicationdraft.dto;

import java.util.List;

public record ExperienceDistributionEntry(Long experienceId, String title, List<String> usedInQuestionIds) {}
