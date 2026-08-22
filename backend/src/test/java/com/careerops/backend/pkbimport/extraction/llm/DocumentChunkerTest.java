package com.careerops.backend.pkbimport.extraction.llm;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DocumentChunkerTest {
    private final DocumentChunker chunker = new DocumentChunker();

    @Test
    void returnsSubThresholdDocumentUnchanged() {
        String text = "section one\n\nsection two";
        assertThat(chunker.chunk(text)).containsExactly(text);
    }

    @Test
    void splitsLongDocumentOnParagraphBoundariesWithoutLossOrOverlap() {
        String paragraph = "기술 Java와 PostgreSQL을 사용한 프로젝트 기록 ".repeat(200) + "\n\n";
        String text = paragraph.repeat(5);

        List<String> chunks = chunker.chunk(text);

        assertThat(chunks).hasSizeGreaterThan(1);
        assertThat(String.join("", chunks)).isEqualTo(text);
        assertThat(chunks).allMatch(chunk -> chunk.endsWith("\n\n"));
    }

    @Test
    void forceSplitsOnlyOversizedParagraphAndPreservesSurroundingParagraphs() {
        String before = "before paragraph\n\n";
        String oversized = "X".repeat(24_100) + "\n\n";
        String after = "after paragraph";
        String text = before + oversized + after;

        List<String> chunks = chunker.chunk(text);

        assertThat(String.join("", chunks)).isEqualTo(text);
        assertThat(chunks.getFirst()).isEqualTo(before);
        assertThat(chunks.getLast()).isEqualTo(after);
        assertThat(chunks.subList(1, chunks.size() - 1)).allMatch(chunk -> chunk.length() <= 12_000);
    }
}
