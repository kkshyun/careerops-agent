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
                .andExpect(jsonPath("$.careerLevel").value("신입"))
                .andExpect(jsonPath("$.educationRequirement").value("대졸"))
                .andExpect(jsonPath("$.status").value("OPEN"))
                .andExpect(jsonPath("$.institutionCode").value("B001"))
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
                "CareerOps Bank", "신입 IT 개발자", "정규직", "신입", "대졸", "OPEN", "B001", "IT/전산", "서울",
                LocalDate.of(2026, 8, 13), LocalDate.of(2026, 8, 31), "MANUAL",
                "https://example.com/jobs/123", "123"
        ));

        mockMvc.perform(get("/api/jobs/{id}", saved.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(saved.getId()))
                .andExpect(jsonPath("$.companyName").value("CareerOps Bank"))
                .andExpect(jsonPath("$.title").value("신입 IT 개발자"))
                .andExpect(jsonPath("$.careerLevel").value("신입"))
                .andExpect(jsonPath("$.educationRequirement").value("대졸"))
                .andExpect(jsonPath("$.status").value("OPEN"))
                .andExpect(jsonPath("$.institutionCode").value("B001"))
                .andExpect(jsonPath("$.source").value("MANUAL"))
                .andExpect(jsonPath("$.createdAt").isNotEmpty());
    }

    @Test
    void returnsNotFoundForUnknownId() throws Exception {
        mockMvc.perform(get("/api/jobs/{id}", Long.MAX_VALUE))
                .andExpect(status().isNotFound());
    }

    @Test
    void getsFilteredJobsWithListResponseFieldsAndFixedOrder() throws Exception {
        JobPosting later = save("한국전력공사", "OPEN", "신입+경력", "정보기술,경영·행정·사무",
                LocalDate.of(2026, 8, 30));
        JobPosting earlier = save("한국전력공사", "OPEN", "신입", "정보기술",
                LocalDate.of(2026, 8, 20));
        save("한국전력공사", "CLOSED", "신입", "정보기술", LocalDate.of(2026, 8, 10));
        save("다른기관", "OPEN", "신입", "정보기술", null);

        mockMvc.perform(get("/api/jobs")
                        .param("status", "OPEN")
                        .param("careerLevel", "신입")
                        .param("companyName", "한국전력")
                        .param("jobCategory", "정보기술"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.content[0].id").value(earlier.getId()))
                .andExpect(jsonPath("$.content[1].id").value(later.getId()))
                .andExpect(jsonPath("$.content[0].companyName").value("한국전력공사"))
                .andExpect(jsonPath("$.content[0].title").value("공고"))
                .andExpect(jsonPath("$.content[0].status").value("OPEN"))
                .andExpect(jsonPath("$.content[0].createdAt").isNotEmpty())
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.totalPages").value(1))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20));
    }

    @Test
    void usesDefaultPaginationAndReturnsSecondPage() throws Exception {
        for (int index = 0; index < 21; index++) {
            save("기관" + index, "OPEN", "신입", "정보기술", LocalDate.of(2026, 8, 1).plusDays(index));
        }

        mockMvc.perform(get("/api/jobs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(20))
                .andExpect(jsonPath("$.totalElements").value(21))
                .andExpect(jsonPath("$.totalPages").value(2))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20));

        mockMvc.perform(get("/api/jobs").param("page", "1").param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.page").value(1));
    }

    @Test
    void clampsRequestedPageSizeToOneHundred() throws Exception {
        for (int index = 0; index < 101; index++) {
            save("기관" + index, "OPEN", "신입", "정보기술", LocalDate.of(2026, 8, 1).plusDays(index));
        }

        mockMvc.perform(get("/api/jobs").param("size", "500"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(100))
                .andExpect(jsonPath("$.totalElements").value(101))
                .andExpect(jsonPath("$.totalPages").value(2))
                .andExpect(jsonPath("$.size").value(100));
    }

    private JobPosting save(
            String companyName, String status, String careerLevel, String jobCategory, LocalDate applicationEndAt) {
        return repository.save(new JobPosting(
                companyName, "공고", null, careerLevel, null, status, null, jobCategory, null,
                null, applicationEndAt, "ALIO", null, companyName
        ));
    }

    private String validRequestJson() {
        return """
                {
                  "companyName": "CareerOps Bank",
                  "title": "신입 IT 개발자",
                  "employmentType": "신입",
                  "careerLevel": "신입",
                  "educationRequirement": "대졸",
                  "status": "OPEN",
                  "institutionCode": "B001",
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
