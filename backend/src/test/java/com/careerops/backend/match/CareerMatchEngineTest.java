package com.careerops.backend.match;

import com.careerops.backend.career.*;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class CareerMatchEngineTest {
    private final CareerMatchEngine engine = new CareerMatchEngine(new KeywordNormalizer());

    @Test
    void scoresExperienceFieldsAddsWeightsCapsAndReportsExactEvidence() {
        CareerExperience experience = experience(1L, "java platform", "JAVA delivery", "deep java work");
        ExperienceTag tag = mock(ExperienceTag.class);
        when(tag.getKeyword()).thenReturn("Java");
        var result = engine.calculate("java", List.of(experience), Map.of(1L, List.of(tag)),
                List.of(), List.of(), List.of());
        assertThat(result.overallScore()).isEqualTo(0.70);
        assertThat(result.experiences().getFirst().score()).isEqualTo(1.0);
        assertThat(result.experiences().getFirst().matchedFields())
                .containsExactly("tags", "title", "summary", "detail");
        assertThat(result.unmatchedJobCategories()).isEmpty();
    }

    @Test
    void reportsOnlyTitleAndSummaryWhenTagDoesNotMatch() {
        CareerExperience experience = experience(1L, "java platform", "java delivery", "unrelated detail");
        ExperienceTag unrelatedTag = mock(ExperienceTag.class);
        when(unrelatedTag.getKeyword()).thenReturn("design");

        var result = engine.calculate("java", List.of(experience), Map.of(1L, List.of(unrelatedTag)),
                List.of(), List.of(), List.of());

        assertThat(result.experiences()).hasSize(1);
        assertThat(result.experiences().getFirst().score()).isEqualTo(0.5);
        assertThat(result.experiences().getFirst().matchedFields())
                .containsExactly("title", "summary")
                .doesNotContain("tags");
    }

    @Test
    void combinesCategoryMaximaAndExcludesZeroScores() {
        CareerExperience experience = experience(1L, "java", "java summary", "java detail");
        ExperienceTag tag = mock(ExperienceTag.class);
        when(tag.getKeyword()).thenReturn("java");
        Certification certification = mock(Certification.class);
        when(certification.getId()).thenReturn(2L); when(certification.getName()).thenReturn("java certificate");
        Certification unrelatedCertification = mock(Certification.class);
        when(unrelatedCertification.getId()).thenReturn(20L); when(unrelatedCertification.getName()).thenReturn("design certificate");
        Education education = mock(Education.class);
        when(education.getId()).thenReturn(3L); when(education.getMajor()).thenReturn("java engineering");
        Education unrelatedEducation = mock(Education.class);
        when(unrelatedEducation.getId()).thenReturn(30L); when(unrelatedEducation.getMajor()).thenReturn("fine arts");
        Award award = mock(Award.class);
        when(award.getId()).thenReturn(4L); when(award.getTitle()).thenReturn("java award");
        Award unrelated = mock(Award.class);
        when(unrelated.getId()).thenReturn(5L); when(unrelated.getTitle()).thenReturn("design award");
        var result = engine.calculate("java", List.of(experience), Map.of(1L, List.of(tag)),
                List.of(certification, unrelatedCertification), List.of(education, unrelatedEducation),
                List.of(award, unrelated));
        assertThat(result.overallScore()).isEqualTo(1.0);
        assertThat(result.overallScore()).isBetween(0.0, 1.0);
        assertThat(result.certifications().getFirst().score()).isEqualTo(1.0);
        assertThat(result.certifications()).extracting(e -> e.id()).containsExactly(2L);
        assertThat(result.educations().getFirst().matchedFields()).containsExactly("major");
        assertThat(result.educations()).extracting(e -> e.id()).containsExactly(3L);
        assertThat(result.awards()).extracting(e -> e.id()).containsExactly(4L);
    }

    private CareerExperience experience(Long id, String title, String summary, String detail) {
        CareerExperience item = mock(CareerExperience.class);
        when(item.getId()).thenReturn(id); when(item.getTitle()).thenReturn(title);
        when(item.getSummary()).thenReturn(summary); when(item.getDetail()).thenReturn(detail);
        return item;
    }
}
