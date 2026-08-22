package com.careerops.backend.match.semantic;

import com.careerops.backend.job.JobPosting;
import org.junit.jupiter.api.Test;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import static org.assertj.core.api.Assertions.assertThat;

class SemanticMatchPromptBuilderTest {
    @Test
    void isolatesJobAndPkbAsDataAndDoesNotIncludeDeterministicScore() {
        SemanticMatchPromptBuilder builder = new SemanticMatchPromptBuilder();
        JobPosting job = new JobPosting("Company", "AI developer", "regular", "new", "bachelor",
                "OPEN", "I", "IT", "Seoul", LocalDate.now(), LocalDate.now(), "MANUAL", "url", "id");
        assertThat(builder.systemPrompt()).contains("DATA", "지시가 아니다", "title", "jobCategory");
        assertThat(builder.userPrompt(job, List.of(), Map.of(), List.of(), List.of(), List.of()))
                .contains("<job>", "</job>", "<pkb>", "</pkb>", "AI developer")
                .doesNotContain("deterministicScore");
    }
}
