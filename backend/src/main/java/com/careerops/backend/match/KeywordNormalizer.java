package com.careerops.backend.match;

import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

@Component
public class KeywordNormalizer {

    public String normalize(String value) {
        if (value == null) {
            return "";
        }
        return value.trim().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
    }

    public List<JobCategory> splitJobCategories(String jobCategory) {
        if (jobCategory == null) {
            return List.of();
        }
        return Arrays.stream(jobCategory.split(",", -1))
                .map(String::trim)
                .filter(raw -> !raw.isEmpty())
                .map(raw -> new JobCategory(raw, normalize(raw)))
                .toList();
    }

    public boolean matches(String left, String right) {
        String normalizedLeft = normalize(left);
        String normalizedRight = normalize(right);
        return !normalizedLeft.isEmpty() && !normalizedRight.isEmpty()
                && (normalizedLeft.contains(normalizedRight) || normalizedRight.contains(normalizedLeft));
    }

    public record JobCategory(String raw, String normalized) {
    }
}
