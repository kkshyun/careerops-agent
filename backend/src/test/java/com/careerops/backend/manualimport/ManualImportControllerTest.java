package com.careerops.backend.manualimport;

import com.careerops.backend.job.JobPostingRepository;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.Rollback;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@Rollback
class ManualImportControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private JobPostingRepository repository;
    @Autowired private MeterRegistry meterRegistry;

    @Test
    void savesJobWithOptionalFieldsMapped() throws Exception {
        mockMvc.perform(post("/api/import/jobs/manual")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(fullRequestJson("https://example.com/jobs/manual-1")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.result").value("saved"))
                .andExpect(jsonPath("$.job.id").isNumber())
                .andExpect(jsonPath("$.job.companyName").value("CareerOps Bank"))
                .andExpect(jsonPath("$.job.title").value("신입 IT 개발자"))
                .andExpect(jsonPath("$.job.employmentType").value("신입"))
                .andExpect(jsonPath("$.job.jobCategory").value("IT/전산"))
                .andExpect(jsonPath("$.job.location").value("서울"))
                .andExpect(jsonPath("$.job.applicationStartAt").value("2026-08-13"))
                .andExpect(jsonPath("$.job.applicationEndAt").value("2026-08-31"))
                .andExpect(jsonPath("$.job.source").value("MANUAL"))
                .andExpect(jsonPath("$.job.sourceUrl").value("https://example.com/jobs/manual-1"))
                .andExpect(jsonPath("$.job.externalId").doesNotExist())
                .andExpect(jsonPath("$.job.createdAt").isNotEmpty());
    }

    @Test
    void savesJobWithOmittedOptionalFieldsAsNull() throws Exception {
        mockMvc.perform(post("/api/import/jobs/manual")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(minimalRequestJson("https://example.com/jobs/manual-2")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.job.employmentType").doesNotExist())
                .andExpect(jsonPath("$.job.jobCategory").doesNotExist())
                .andExpect(jsonPath("$.job.location").doesNotExist())
                .andExpect(jsonPath("$.job.applicationStartAt").doesNotExist())
                .andExpect(jsonPath("$.job.applicationEndAt").doesNotExist());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{}",
            "{\"sourceUrl\":\"\"}",
            "{\"sourceUrl\":\" \"}",
            "{\"sourceUrl\":\"not-a-url\"}",
            "{\"sourceUrl\":\"javascript:alert(1)\"}",
            "{\"sourceUrl\":\"file:///etc/passwd\"}",
            "{\"sourceUrl\":\"ftp://example.com/a\"}"
    })
    void rejectsMissingBlankMalformedAndForbiddenSchemeUrls(String sourceUrlFragment) throws Exception {
        long rowsBefore = repository.count();
        String request = sourceUrlFragment.equals("{}")
                ? "{\"companyName\":\"CareerOps Bank\",\"title\":\"Developer\"}"
                : sourceUrlFragment.substring(0, sourceUrlFragment.length() - 1)
                        + ",\"companyName\":\"CareerOps Bank\",\"title\":\"Developer\"}";

        mockMvc.perform(post("/api/import/jobs/manual")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isBadRequest());

        assertThat(repository.count()).isEqualTo(rowsBefore);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{\"title\":\"Developer\"}",
            "{\"companyName\":\"\",\"title\":\"Developer\"}",
            "{\"companyName\":\" \",\"title\":\"Developer\"}",
            "{\"companyName\":\"CareerOps Bank\"}",
            "{\"companyName\":\"CareerOps Bank\",\"title\":\"\"}",
            "{\"companyName\":\"CareerOps Bank\",\"title\":\" \"}"
    })
    void rejectsMissingAndBlankCompanyNameOrTitle(String requiredFieldsFragment) throws Exception {
        long rowsBefore = repository.count();
        String request = requiredFieldsFragment.substring(0, requiredFieldsFragment.length() - 1)
                + ",\"sourceUrl\":\"https://example.com/jobs/invalid-required\"}";

        mockMvc.perform(post("/api/import/jobs/manual")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isBadRequest());

        assertThat(repository.count()).isEqualTo(rowsBefore);
    }

    @Test
    void returnsExistingJobForDuplicateAndMaintainsMetricInvariant() throws Exception {
        String url = "https://example.com/jobs/manual-duplicate";
        double savedBefore = counter("careerops.manual.import", "result", "saved");
        double duplicateBefore = counter("careerops.manual.import", "result", "duplicate");
        double creationBefore = meterRegistry.counter("careerops.job.creation").count();

        MvcResult first = mockMvc.perform(post("/api/import/jobs/manual")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(minimalRequestJson(url)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.result").value("saved"))
                .andReturn();
        long countAfterFirst = repository.count();

        MvcResult second = mockMvc.perform(post("/api/import/jobs/manual")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(minimalRequestJson(url)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result").value("duplicate"))
                .andReturn();

        JsonNode firstBody = objectMapper.readTree(first.getResponse().getContentAsString());
        JsonNode secondBody = objectMapper.readTree(second.getResponse().getContentAsString());
        assertThat(secondBody.get("job").get("id").asLong())
                .isEqualTo(firstBody.get("job").get("id").asLong());
        assertThat(repository.count()).isEqualTo(countAfterFirst);
        assertThat(counter("careerops.manual.import", "result", "saved")).isEqualTo(savedBefore + 1);
        assertThat(counter("careerops.manual.import", "result", "duplicate")).isEqualTo(duplicateBefore + 1);
        assertThat(meterRegistry.counter("careerops.job.creation").count()).isEqualTo(creationBefore + 1);
    }

    private double counter(String name, String tagName, String tagValue) {
        return meterRegistry.counter(name, tagName, tagValue).count();
    }

    private String minimalRequestJson(String sourceUrl) {
        return """
                {
                  "sourceUrl": "%s",
                  "companyName": "CareerOps Bank",
                  "title": "신입 IT 개발자"
                }
                """.formatted(sourceUrl);
    }

    private String fullRequestJson(String sourceUrl) {
        return """
                {
                  "sourceUrl": "%s",
                  "companyName": "CareerOps Bank",
                  "title": "신입 IT 개발자",
                  "employmentType": "신입",
                  "jobCategory": "IT/전산",
                  "location": "서울",
                  "applicationStartAt": "2026-08-13",
                  "applicationEndAt": "2026-08-31"
                }
                """.formatted(sourceUrl);
    }
}
