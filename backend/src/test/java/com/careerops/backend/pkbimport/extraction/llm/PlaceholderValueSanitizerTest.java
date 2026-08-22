package com.careerops.backend.pkbimport.extraction.llm;

import com.careerops.backend.career.dto.AwardCreateRequest;
import com.careerops.backend.career.dto.CertificationCreateRequest;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PlaceholderValueSanitizerTest {
    private final PlaceholderValueSanitizer sanitizer = new PlaceholderValueSanitizer();

    @Test
    void replacesExactPlaceholdersIgnoringWhitespaceAndCase() {
        StructuredExtractionResult result = sanitizer.sanitize(new StructuredExtractionResult(List.of(),
                List.of(new CertificationCreateRequest(" N/A ", "  ", null, null, "-", "TbD")),
                List.of(), List.of(new AwardCreateRequest("없음", "미상", null, "해당 없음"))));

        CertificationCreateRequest certification = result.certifications().getFirst();
        assertThat(certification.name()).isNull();
        assertThat(certification.issuer()).isNull();
        assertThat(certification.credentialId()).isNull();
        assertThat(certification.description()).isNull();
        assertThat(result.awards().getFirst().title()).isNull();
        assertThat(result.awards().getFirst().issuer()).isNull();
    }

    @Test
    void doesNotReplacePartialMatches() {
        StructuredExtractionResult result = sanitizer.sanitize(new StructuredExtractionResult(List.of(), List.of(),
                List.of(), List.of(new AwardCreateRequest("현재 진행", "N/A 협회", null, null))));
        assertThat(result.awards().getFirst().title()).isEqualTo("현재 진행");
        assertThat(result.awards().getFirst().issuer()).isEqualTo("N/A 협회");
    }
}
