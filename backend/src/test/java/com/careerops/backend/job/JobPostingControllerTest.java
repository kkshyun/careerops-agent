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
    private RecruitmentStepRepository recruitmentStepRepository;

    @Autowired
    private AttachmentRepository attachmentRepository;

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
                .andExpect(jsonPath("$.createdAt").isNotEmpty())
                .andExpect(jsonPath("$.recruitmentSteps").isEmpty())
                .andExpect(jsonPath("$.attachments").isEmpty());
    }

    @Test
    void getsRecruitmentStepsAndAttachmentsWithPublicFieldsInStableOrder() throws Exception {
        JobPosting saved = save("상세기관", "OPEN", "신입", "정보기술", LocalDate.of(2026, 8, 31));
        recruitmentStepRepository.saveAllAndFlush(java.util.List.of(
                new RecruitmentStep(saved, 9002L, 1, 1L, 2L, "면접", 3.5, 35, 10, "2026-08-20"),
                new RecruitmentStep(saved, 9001L, 1, 1L, 2L, "서류", 5.0, 50, 10, "2026-08-10")
        ));
        attachmentRepository.saveAllAndFlush(java.util.List.of(
                new Attachment(saved, 8002L, 1, "두번째.pdf", "PDF", "https://example.com/second.pdf"),
                new Attachment(saved, 8001L, 1, "첫번째.hwp", "HWP", "https://example.com/first.hwp")
        ));

        mockMvc.perform(get("/api/jobs/{id}", saved.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.recruitmentSteps.length()").value(2))
                .andExpect(jsonPath("$.recruitmentSteps[0].stepGroupName").value("서류"))
                .andExpect(jsonPath("$.recruitmentSteps[0].sortNo").value(1))
                .andExpect(jsonPath("$.recruitmentSteps[0].competitionRate").value(5.0))
                .andExpect(jsonPath("$.recruitmentSteps[0].applicantCount").value(50))
                .andExpect(jsonPath("$.recruitmentSteps[0].recruitCount").value(10))
                .andExpect(jsonPath("$.recruitmentSteps[0].occurredAtRaw").value("2026-08-10"))
                .andExpect(jsonPath("$.recruitmentSteps[0].id").doesNotExist())
                .andExpect(jsonPath("$.recruitmentSteps[0].recrutStepSn").doesNotExist())
                .andExpect(jsonPath("$.recruitmentSteps[0].minStepSn").doesNotExist())
                .andExpect(jsonPath("$.recruitmentSteps[0].maxStepSn").doesNotExist())
                .andExpect(jsonPath("$.recruitmentSteps[0].jobPosting").doesNotExist())
                .andExpect(jsonPath("$.recruitmentSteps[0].createdAt").doesNotExist())
                .andExpect(jsonPath("$.recruitmentSteps[1].stepGroupName").value("면접"))
                .andExpect(jsonPath("$.attachments.length()").value(2))
                .andExpect(jsonPath("$.attachments[0].fileName").value("첫번째.hwp"))
                .andExpect(jsonPath("$.attachments[0].sortNo").value(1))
                .andExpect(jsonPath("$.attachments[0].fileType").value("HWP"))
                .andExpect(jsonPath("$.attachments[0].url").value("https://example.com/first.hwp"))
                .andExpect(jsonPath("$.attachments[0].id").doesNotExist())
                .andExpect(jsonPath("$.attachments[0].recrutAtchFileNo").doesNotExist())
                .andExpect(jsonPath("$.attachments[0].jobPosting").doesNotExist())
                .andExpect(jsonPath("$.attachments[0].createdAt").doesNotExist())
                .andExpect(jsonPath("$.attachments[1].fileName").value("두번째.pdf"));
    }

    @Test
    void getsEmptyDetailArraysForAlioJobWithoutDetails() throws Exception {
        JobPosting saved = save("미보강기관", "OPEN", "신입", "정보기술", LocalDate.of(2026, 8, 31));

        mockMvc.perform(get("/api/jobs/{id}", saved.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.recruitmentSteps").isArray())
                .andExpect(jsonPath("$.recruitmentSteps").isEmpty())
                .andExpect(jsonPath("$.attachments").isArray())
                .andExpect(jsonPath("$.attachments").isEmpty());
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
                .andExpect(jsonPath("$.content[0].recruitmentSteps").doesNotExist())
                .andExpect(jsonPath("$.content[0].attachments").doesNotExist())
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
                null, applicationEndAt, "ALIO", null, java.util.UUID.randomUUID().toString()
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
