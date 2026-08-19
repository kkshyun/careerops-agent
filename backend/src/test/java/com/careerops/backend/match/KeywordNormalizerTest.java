package com.careerops.backend.match;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class KeywordNormalizerTest {
    private final KeywordNormalizer normalizer = new KeywordNormalizer();

    @Test
    void normalizesCaseAndWhitespaceAndMatchesBidirectionally() {
        assertThat(normalizer.normalize("  JAVA\t Backend  ")).isEqualTo("java backend");
        assertThat(normalizer.matches("Java Backend", "java")).isTrue();
        assertThat(normalizer.matches("java", "JAVA Backend")).isTrue();
        assertThat(normalizer.matches(" ", "java")).isFalse();
        assertThat(normalizer.matches(null, "java")).isFalse();
    }

    @Test
    void splitsOnlyOnCommasAndDropsEmptyPiecesWhileKeepingRawText() {
        var categories = normalizer.splitJobCategories("  Java.Backend ,, DATA  ,");
        assertThat(categories).extracting(KeywordNormalizer.JobCategory::raw)
                .containsExactly("Java.Backend", "DATA");
        assertThat(categories).extracting(KeywordNormalizer.JobCategory::normalized)
                .containsExactly("java.backend", "data");
        assertThat(normalizer.splitJobCategories(" , , ")).isEmpty();
        assertThat(normalizer.splitJobCategories(null)).isEmpty();
    }
}
