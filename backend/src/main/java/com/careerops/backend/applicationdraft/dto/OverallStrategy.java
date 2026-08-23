package com.careerops.backend.applicationdraft.dto;

import java.util.List;

public record OverallStrategy(String primaryPositioning,
        List<ExperienceDistributionEntry> experienceDistribution, List<String> warnings) {}
