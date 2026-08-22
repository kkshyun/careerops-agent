package com.careerops.backend.pkbimport.extraction.llm;

import com.careerops.backend.pkbimport.DocumentType;
import org.springframework.context.annotation.Primary;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
@Primary
public class ChunkingDocumentExtractionClient implements DocumentExtractionClient {
    private final DocumentExtractionClient delegate;
    private final DocumentChunker chunker;

    @Autowired
    public ChunkingDocumentExtractionClient(AnthropicDocumentExtractionClient delegate, DocumentChunker chunker) {
        this.delegate = delegate;
        this.chunker = chunker;
    }

    ChunkingDocumentExtractionClient(DocumentExtractionClient delegate, DocumentChunker chunker) {
        this.delegate = delegate;
        this.chunker = chunker;
    }

    @Override
    public StructuredExtractionResult extract(String rawText, DocumentType documentType) {
        if (rawText.length() < DocumentChunker.CHUNK_THRESHOLD) {
            return delegate.extract(rawText, documentType);
        }

        List<StructuredExtractionResult> results = chunker.chunk(rawText).stream()
                .map(chunk -> delegate.extract(chunk, documentType))
                .toList();
        return new StructuredExtractionResult(
                merge(results, StructuredExtractionResult::careerExperiences),
                merge(results, StructuredExtractionResult::certifications),
                merge(results, StructuredExtractionResult::educations),
                merge(results, StructuredExtractionResult::awards));
    }

    private <T> List<T> merge(List<StructuredExtractionResult> results,
                              java.util.function.Function<StructuredExtractionResult, List<T>> values) {
        List<T> merged = new ArrayList<>();
        results.forEach(result -> merged.addAll(values.apply(result)));
        return merged.stream().distinct().toList();
    }
}
