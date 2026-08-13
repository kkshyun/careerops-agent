package com.careerops.backend.collector;

public record CollectResult(
        String source,
        int fetched,
        int saved,
        int skipped,
        int failed,
        String result
) {
}
