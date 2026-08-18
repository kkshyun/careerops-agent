package com.careerops.backend.pkbimport.extraction;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PdfTextExtractorTest {
    private final PdfTextExtractor extractor = new PdfTextExtractor();

    @Test
    void extractsTextAndPreservesPageOrder() throws Exception {
        String extracted = extractor.extract(new ByteArrayInputStream(DocumentFixtureSupport.pdf("first page", "second page")));
        assertThat(extracted).contains("first page", "second page");
        assertThat(extracted.indexOf("first page")).isLessThan(extracted.indexOf("second page"));
    }

    @Test
    void returnsBlankForPdfWithoutText() throws Exception {
        assertThat(extractor.extract(new ByteArrayInputStream(DocumentFixtureSupport.pdf((String) null)))).isBlank();
    }

    @Test
    void wrapsCorruptAndPasswordProtectedPdfWithoutParserMessage() throws Exception {
        assertThatThrownBy(() -> extractor.extract(new ByteArrayInputStream("%PDF-corrupt-private-marker".getBytes())))
                .isInstanceOf(DocumentExtractionException.class).hasMessage("PDF extraction failed");
        assertThatThrownBy(() -> extractor.extract(new ByteArrayInputStream(DocumentFixtureSupport.protectedPdf("secret"))))
                .isInstanceOf(DocumentExtractionException.class)
                .satisfies(error -> assertThat(error.getMessage()).doesNotContain("user-password"));
    }
}
