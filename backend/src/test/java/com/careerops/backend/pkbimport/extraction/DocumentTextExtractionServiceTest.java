package com.careerops.backend.pkbimport.extraction;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DocumentTextExtractionServiceTest {
    @Test
    void dispatchesToExtractorSupportingExtension() {
        DocumentTextExtractor pdf = new StubExtractor("pdf", "pdf text");
        DocumentTextExtractor docx = new StubExtractor("docx", "docx text");
        DocumentTextExtractionService service = new DocumentTextExtractionService(List.of(pdf, docx));

        assertThat(service.extract("pdf", new ByteArrayInputStream(new byte[0]))).isEqualTo("pdf text");
        assertThat(service.extract("docx", new ByteArrayInputStream(new byte[0]))).isEqualTo("docx text");
        assertThatThrownBy(() -> service.extract("txt", new ByteArrayInputStream(new byte[0])))
                .isInstanceOf(DocumentExtractionException.class);
    }

    private record StubExtractor(String extension, String text) implements DocumentTextExtractor {
        @Override public boolean supports(String value) { return extension.equals(value); }
        @Override public String extract(java.io.InputStream inputStream) { return text; }
    }
}
