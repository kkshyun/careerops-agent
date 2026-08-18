package com.careerops.backend.pkbimport.extraction;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DocxTextExtractorTest {
    private final DocxTextExtractor extractor = new DocxTextExtractor();

    @Test
    void extractsParagraphsAndTableInDocumentOrder() throws Exception {
        String text = extractor.extract(new ByteArrayInputStream(
                DocumentFixtureSupport.docx("before-table", true, "after-table")));
        assertThat(text).contains("before-table", "left-cell\tright-cell", "after-table");
        assertThat(text.indexOf("before-table")).isLessThan(text.indexOf("left-cell"));
        assertThat(text.indexOf("left-cell")).isLessThan(text.indexOf("after-table"));
    }

    @Test
    void wrapsCorruptDocxWithFixedMessage() {
        assertThatThrownBy(() -> extractor.extract(new ByteArrayInputStream(
                new byte[]{'P', 'K', 3, 4, 9, 8, 7})))
                .isInstanceOf(DocumentExtractionException.class).hasMessage("DOCX extraction failed");
    }
}
