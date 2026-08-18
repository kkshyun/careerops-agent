package com.careerops.backend.application;

import com.careerops.backend.job.Attachment;
import com.careerops.backend.job.AttachmentRepository;
import com.careerops.backend.job.JobPosting;
import com.careerops.backend.job.JobPostingRepository;
import com.careerops.backend.job.RecruitmentStep;
import com.careerops.backend.job.RecruitmentStepRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.Rollback;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@Rollback
class JobApplicationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JobApplicationRepository repository;

    @Autowired
    private JobPostingRepository jobPostingRepository;

    @Autowired
    private RecruitmentStepRepository recruitmentStepRepository;

    @Autowired
    private AttachmentRepository attachmentRepository;

    @Autowired
    private ApplicationStageRepository applicationStageRepository;

    @Test
    void createsWithDefaultStatusAndReturnsPostingSnapshot() throws Exception {
        JobPosting posting = savePosting("CareerOps Bank", "신입 IT 개발자", "MANUAL", "OPEN");

        mockMvc.perform(post("/api/applications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"jobPostingId":%d,"memo":"금융 IT 직무"}
                                """.formatted(posting.getId())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.status").value("INTERESTED"))
                .andExpect(jsonPath("$.memo").value("금융 IT 직무"))
                .andExpect(jsonPath("$.appliedAt").doesNotExist())
                .andExpect(jsonPath("$.createdAt").isNotEmpty())
                .andExpect(jsonPath("$.updatedAt").isNotEmpty())
                .andExpect(jsonPath("$.jobPostingId").value(posting.getId()))
                .andExpect(jsonPath("$.companyName").value("CareerOps Bank"))
                .andExpect(jsonPath("$.title").value("신입 IT 개발자"))
                .andExpect(jsonPath("$.applicationEndAt").value("2026-08-31"))
                .andExpect(jsonPath("$.jobPostingStatus").value("OPEN"))
                .andExpect(jsonPath("$.stages").doesNotExist());
    }

    @Test
    void getsApplicationDetailWithStagesSortedByOrder() throws Exception {
        JobApplication application = saveApplication(
                savePosting("기관", "공고", "MANUAL", "OPEN"), ApplicationStatus.INTERESTED, null);
        ApplicationStage later = applicationStageRepository.saveAndFlush(new ApplicationStage(
                application, StageType.INTERVIEW, "면접", 2, null, StageResult.PENDING, null));
        ApplicationStage earlier = applicationStageRepository.saveAndFlush(new ApplicationStage(
                application, StageType.DOCUMENT, "서류", 1, null, StageResult.PASSED, null));

        mockMvc.perform(get("/api/applications/{id}", application.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stages.length()").value(2))
                .andExpect(jsonPath("$.stages[0].id").value(earlier.getId()))
                .andExpect(jsonPath("$.stages[1].id").value(later.getId()));
    }

    @Test
    void listResponseDoesNotContainStagesEvenWhenStagesExist() throws Exception {
        JobApplication application = saveApplication(
                savePosting("기관", "공고", "MANUAL", "OPEN"), ApplicationStatus.INTERESTED, null);
        applicationStageRepository.saveAndFlush(new ApplicationStage(
                application, StageType.DOCUMENT, null, 1, null, StageResult.PENDING, null));

        mockMvc.perform(get("/api/applications"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].stages").doesNotExist());
    }

    @Test
    void returnsNotFoundForUnknownPostingAndDoesNotSave() throws Exception {
        long countBefore = repository.count();

        mockMvc.perform(post("/api/applications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"jobPostingId\":9223372036854775807}"))
                .andExpect(status().isNotFound());

        assertThat(repository.count()).isEqualTo(countBefore);
    }

    @Test
    void rejectsDuplicatePostingWithoutSavingSecondRow() throws Exception {
        JobPosting posting = savePosting("기관", "공고", "MANUAL", "OPEN");
        String request = "{\"jobPostingId\":%d}".formatted(posting.getId());
        mockMvc.perform(post("/api/applications").contentType(MediaType.APPLICATION_JSON).content(request))
                .andExpect(status().isCreated());
        long countAfterFirst = repository.count();

        mockMvc.perform(post("/api/applications").contentType(MediaType.APPLICATION_JSON).content(request))
                .andExpect(status().isConflict());

        assertThat(repository.count()).isEqualTo(countAfterFirst);
    }

    @Test
    void getsApplicationAndReturnsNotFoundForUnknownId() throws Exception {
        JobPosting posting = savePosting("한국전력공사", "신입 IT 개발자", "MANUAL", "OPEN");
        JobApplication application = saveApplication(posting, ApplicationStatus.INTERESTED, "메모");

        mockMvc.perform(get("/api/applications/{id}", application.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(application.getId()))
                .andExpect(jsonPath("$.companyName").value("한국전력공사"))
                .andExpect(jsonPath("$.title").value("신입 IT 개발자"))
                .andExpect(jsonPath("$.applicationEndAt").value("2026-08-31"))
                .andExpect(jsonPath("$.jobPostingStatus").value("OPEN"));

        mockMvc.perform(get("/api/applications/{id}", Long.MAX_VALUE))
                .andExpect(status().isNotFound());
    }

    @Test
    void listsInUpdatedAtDescendingOrderAndFiltersByStatus() throws Exception {
        JobApplication older = saveApplication(
                savePosting("기관1", "공고1", "MANUAL", "OPEN"), ApplicationStatus.SUBMITTED, null);
        Thread.sleep(5);
        JobApplication newer = saveApplication(
                savePosting("기관2", "공고2", "ALIO", "OPEN"), ApplicationStatus.SUBMITTED, null);
        saveApplication(savePosting("기관3", "공고3", "ALIO", "CLOSED"), ApplicationStatus.PLANNED, null);

        mockMvc.perform(get("/api/applications").param("status", "SUBMITTED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.content[0].id").value(newer.getId()))
                .andExpect(jsonPath("$.content[1].id").value(older.getId()))
                .andExpect(jsonPath("$.totalElements").value(2));
    }

    @Test
    void usesDefaultPaginationAndClampsPageSize() throws Exception {
        for (int index = 0; index < 101; index++) {
            saveApplication(savePosting("기관" + index, "공고", "MANUAL", "OPEN"),
                    ApplicationStatus.INTERESTED, null);
        }

        mockMvc.perform(get("/api/applications"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(20))
                .andExpect(jsonPath("$.totalElements").value(101))
                .andExpect(jsonPath("$.totalPages").value(6))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20));

        mockMvc.perform(get("/api/applications").param("size", "500"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(100))
                .andExpect(jsonPath("$.totalPages").value(2))
                .andExpect(jsonPath("$.size").value(100));
    }

    @Test
    void patchesFieldsIndependentlyAndUpdatesTimestamp() throws Exception {
        JobPosting posting = savePosting("기관", "공고", "MANUAL", "OPEN");
        JobApplication application = saveApplication(posting, ApplicationStatus.INTERESTED, "기존 메모");
        java.time.Instant originalUpdatedAt = application.getUpdatedAt();

        mockMvc.perform(patch("/api/applications/{id}", application.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"SUBMITTED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUBMITTED"))
                .andExpect(jsonPath("$.memo").value("기존 메모"));

        Thread.sleep(5);
        mockMvc.perform(patch("/api/applications/{id}", application.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"memo\":\"변경 메모\",\"appliedAt\":\"2026-08-18\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUBMITTED"))
                .andExpect(jsonPath("$.memo").value("변경 메모"))
                .andExpect(jsonPath("$.appliedAt").value("2026-08-18"));

        assertThat(repository.findById(application.getId()).orElseThrow().getUpdatedAt())
                .isAfter(originalUpdatedAt);
    }

    @Test
    void returnsNotFoundWhenPatchingUnknownId() throws Exception {
        mockMvc.perform(patch("/api/applications/{id}", Long.MAX_VALUE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"SUBMITTED\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void deletesApplicationWithoutDeletingJobPosting() throws Exception {
        JobPosting posting = savePosting("기관", "공고", "MANUAL", "OPEN");
        JobApplication application = saveApplication(posting, ApplicationStatus.INTERESTED, null);

        mockMvc.perform(delete("/api/applications/{id}", application.getId()))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/applications/{id}", application.getId()))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/jobs/{id}", posting.getId()))
                .andExpect(status().isOk());
        mockMvc.perform(delete("/api/applications/{id}", Long.MAX_VALUE))
                .andExpect(status().isNotFound());
    }

    @Test
    void createsForAlioPostingWithEnrichment() throws Exception {
        JobPosting posting = savePosting("ALIO 기관", "전산 채용", "ALIO", "OPEN");
        recruitmentStepRepository.saveAndFlush(
                new RecruitmentStep(posting, 1L, 1, 1L, 1L, "서류", null, null, null, null));
        attachmentRepository.saveAndFlush(
                new Attachment(posting, 1L, 1, "공고.pdf", "PDF", "https://example.com/job.pdf"));

        mockMvc.perform(post("/api/applications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"jobPostingId\":%d,\"status\":\"PLANNED\"}".formatted(posting.getId())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PLANNED"))
                .andExpect(jsonPath("$.companyName").value("ALIO 기관"));
    }

    private JobPosting savePosting(String companyName, String title, String source, String status) {
        return jobPostingRepository.saveAndFlush(new JobPosting(
                companyName, title, null, "신입", null, status, null, "정보기술", null,
                null, LocalDate.of(2026, 8, 31), source, null,
                java.util.UUID.randomUUID().toString()));
    }

    private JobApplication saveApplication(
            JobPosting posting, ApplicationStatus status, String memo) {
        return repository.saveAndFlush(new JobApplication(posting, status, memo, null));
    }
}
