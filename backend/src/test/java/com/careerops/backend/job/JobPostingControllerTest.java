package com.careerops.backend.job;

import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.Rollback;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@Rollback
class JobPostingControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JobPostingRepository repository;

    @Autowired
    private MeterRegistry meterRegistry;

    @Test
    void createsJobPostingAndIncrementsMetric() throws Exception {
        double countBefore = meterRegistry.counter("careerops.job.creation").count();

        mockMvc.perform(post("/api/jobs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequestJson()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.companyName").value("CareerOps Bank"))
                .andExpect(jsonPath("$.title").value("신입 IT 개발자"))
                .andExpect(jsonPath("$.employmentType").value("신입"))
                .andExpect(jsonPath("$.jobCategory").value("IT/전산"))
                .andExpect(jsonPath("$.location").value("서울"))
                .andExpect(jsonPath("$.applicationStartAt").value("2026-08-13"))
                .andExpect(jsonPath("$.applicationEndAt").value("2026-08-31"))
                .andExpect(jsonPath("$.source").value("MANUAL"))
                .andExpect(jsonPath("$.sourceUrl").value("https://example.com/jobs/123"))
                .andExpect(jsonPath("$.externalId").value("123"))
                .andExpect(jsonPath("$.createdAt").isNotEmpty());

        assertThat(meterRegistry.counter("careerops.job.creation").count())
                .isGreaterThanOrEqualTo(countBefore + 1);
    }

    @Test
    void rejectsBlankRequiredFieldWithoutSaving() throws Exception {
        long countBefore = repository.count();

        mockMvc.perform(post("/api/jobs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"companyName":"CareerOps Bank","title":" ","source":"MANUAL"}
                                """))
                .andExpect(status().isBadRequest());

        assertThat(repository.count()).isEqualTo(countBefore);
    }

    @Test
    void getsExistingJobPosting() throws Exception {
        JobPosting saved = repository.saveAndFlush(new JobPosting(
                "CareerOps Bank", "신입 IT 개발자", "신입", "IT/전산", "서울",
                LocalDate.of(2026, 8, 13), LocalDate.of(2026, 8, 31), "MANUAL",
                "https://example.com/jobs/123", "123"
        ));

        mockMvc.perform(get("/api/jobs/{id}", saved.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(saved.getId()))
                .andExpect(jsonPath("$.companyName").value("CareerOps Bank"))
                .andExpect(jsonPath("$.title").value("신입 IT 개발자"))
                .andExpect(jsonPath("$.source").value("MANUAL"))
                .andExpect(jsonPath("$.createdAt").isNotEmpty());
    }

    @Test
    void returnsNotFoundForUnknownId() throws Exception {
        mockMvc.perform(get("/api/jobs/{id}", Long.MAX_VALUE))
                .andExpect(status().isNotFound());
    }

    private String validRequestJson() {
        return """
                {
                  "companyName": "CareerOps Bank",
                  "title": "신입 IT 개발자",
                  "employmentType": "신입",
                  "jobCategory": "IT/전산",
                  "location": "서울",
                  "applicationStartAt": "2026-08-13",
                  "applicationEndAt": "2026-08-31",
                  "source": "MANUAL",
                  "sourceUrl": "https://example.com/jobs/123",
                  "externalId": "123"
                }
                """;
    }
}
