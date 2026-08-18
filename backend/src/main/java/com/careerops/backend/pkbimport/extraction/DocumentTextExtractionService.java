package com.careerops.backend.pkbimport.extraction;

import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.util.List;

@Service
public class DocumentTextExtractionService {
    private final List<DocumentTextExtractor> extractors;

    public DocumentTextExtractionService(List<DocumentTextExtractor> extractors) {
        this.extractors = extractors;
    }

    public String extract(String lowerCaseExtension, InputStream inputStream) {
        return extractors.stream()
                .filter(extractor -> extractor.supports(lowerCaseExtension))
                .findFirst()
                .orElseThrow(() -> new DocumentExtractionException("Unsupported document extension"))
                .extract(inputStream);
    }
}
