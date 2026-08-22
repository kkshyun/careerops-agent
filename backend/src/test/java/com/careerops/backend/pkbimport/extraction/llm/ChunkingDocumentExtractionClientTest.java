package com.careerops.backend.pkbimport.extraction.llm;

import com.careerops.backend.career.EducationDegree;
import com.careerops.backend.career.ExperienceType;
import com.careerops.backend.career.dto.*;
import com.careerops.backend.pkbimport.DocumentType;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ChunkingDocumentExtractionClientTest {
    @Test
    void delegatesSubThresholdInputExactlyOnceAndUnchanged() {
        RecordingClient delegate = new RecordingClient(List.of(empty()));
        ChunkingDocumentExtractionClient client = new ChunkingDocumentExtractionClient(delegate, new DocumentChunker());

        StructuredExtractionResult result = client.extract("original", DocumentType.RESUME);

        assertThat(result).isEqualTo(empty());
        assertThat(delegate.inputs).containsExactly("original");
    }

    @Test
    void concatenatesAllFourTypesInChunkOrder() {
        String text = longDocument();
        List<String> chunks = new DocumentChunker().chunk(text);
        List<StructuredExtractionResult> results = new ArrayList<>();
        for (int i = 0; i < chunks.size(); i++) results.add(result(i));
        RecordingClient delegate = new RecordingClient(results);

        StructuredExtractionResult merged = new ChunkingDocumentExtractionClient(delegate, new DocumentChunker())
                .extract(text, DocumentType.EXPERIENCE_NOTE);

        assertThat(delegate.inputs).containsExactlyElementsOf(chunks);
        assertThat(merged.careerExperiences()).extracting(CareerExperienceCreateRequest::title)
                .containsExactlyElementsOf(indexed("career", chunks.size()));
        assertThat(merged.certifications()).extracting(CertificationCreateRequest::name)
                .containsExactlyElementsOf(indexed("cert", chunks.size()));
        assertThat(merged.educations()).extracting(EducationCreateRequest::institution)
                .containsExactlyElementsOf(indexed("school", chunks.size()));
        assertThat(merged.awards()).extracting(AwardCreateRequest::title)
                .containsExactlyElementsOf(indexed("award", chunks.size()));
    }

    @Test
    void propagatesFailureImmediatelyWithoutCallingLaterChunks() {
        String text = longDocument();
        LlmExtractionException failure = new LlmExtractionException(LlmExtractionException.Reason.NETWORK_TIMEOUT);
        RecordingClient delegate = new RecordingClient(List.of(result(0), failure));

        assertThatThrownBy(() -> new ChunkingDocumentExtractionClient(delegate, new DocumentChunker())
                .extract(text, DocumentType.RESUME)).isSameAs(failure);
        assertThat(delegate.inputs).hasSize(2);
    }

    @Test
    void removesOnlyCompletelyEqualRecordsAndPreservesFirstOccurrenceOrder() {
        String text = longDocument();
        CareerExperienceCreateRequest same = career("same");
        CareerExperienceCreateRequest different = career("different");
        RecordingClient delegate = new RecordingClient(List.of(
                new StructuredExtractionResult(List.of(same), List.of(), List.of(), List.of()),
                new StructuredExtractionResult(List.of(same, different), List.of(), List.of(), List.of()),
                empty()));

        StructuredExtractionResult merged = new ChunkingDocumentExtractionClient(delegate, new DocumentChunker())
                .extract(text, DocumentType.RESUME);

        assertThat(merged.careerExperiences()).containsExactly(same, different);
    }

    private static String longDocument() {
        return ("명시된 합성 프로젝트 문단 ".repeat(600) + "\n\n").repeat(3);
    }

    private static StructuredExtractionResult result(int index) {
        return new StructuredExtractionResult(List.of(career("career" + index)),
                List.of(new CertificationCreateRequest("cert" + index, null, null, null, null, null)),
                List.of(new EducationCreateRequest("school" + index, null, EducationDegree.BACHELOR,
                        null, null, null, null, null, null)),
                List.of(new AwardCreateRequest("award" + index, null, null, null)));
    }

    private static CareerExperienceCreateRequest career(String title) {
        return new CareerExperienceCreateRequest(ExperienceType.PROJECT, title, null, null,
                null, null, null, null, List.of(), List.of());
    }

    private static StructuredExtractionResult empty() {
        return new StructuredExtractionResult(List.of(), List.of(), List.of(), List.of());
    }

    private static List<String> indexed(String prefix, int size) {
        return java.util.stream.IntStream.range(0, size).mapToObj(i -> prefix + i).toList();
    }

    private static class RecordingClient implements DocumentExtractionClient {
        private final List<?> outcomes;
        private final List<String> inputs = new ArrayList<>();
        private int call;

        private RecordingClient(List<?> outcomes) { this.outcomes = outcomes; }

        @Override public StructuredExtractionResult extract(String rawText, DocumentType documentType) {
            inputs.add(rawText);
            Object outcome = outcomes.get(call++);
            if (outcome instanceof LlmExtractionException failure) throw failure;
            return (StructuredExtractionResult) outcome;
        }
    }
}
