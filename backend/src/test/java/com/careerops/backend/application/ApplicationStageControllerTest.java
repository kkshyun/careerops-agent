package com.careerops.backend.application;

import com.careerops.backend.job.JobPosting;
import com.careerops.backend.job.JobPostingRepository;
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
class ApplicationStageControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired ApplicationStageRepository stageRepository;
    @Autowired JobApplicationRepository applicationRepository;
    @Autowired JobPostingRepository postingRepository;

    @Test
    void createsWithAutomaticOrderAndDefaultResultAndAllowsRepeatedType() throws Exception {
        JobApplication application = saveApplication(ApplicationStatus.INTERESTED);

        createStage(application.getId(), "{\"stageType\":\"INTERVIEW\",\"label\":\"1차 면접\"}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.sortOrder").value(1))
                .andExpect(jsonPath("$.result").value("PENDING"))
                .andExpect(jsonPath("$.jobApplicationId").doesNotExist());
        createStage(application.getId(), "{\"stageType\":\"INTERVIEW\",\"label\":\"2차 면접\"}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.sortOrder").value(2));
        createStage(application.getId(), "{\"stageType\":\"FINAL\"}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.sortOrder").value(3));

        assertThat(stageRepository.count()).isEqualTo(3);
    }

    @Test
    void rejectsUnknownApplicationAndMissingStageType() throws Exception {
        createStage(Long.MAX_VALUE, "{\"stageType\":\"DOCUMENT\"}")
                .andExpect(status().isNotFound());
        createStage(saveApplication(ApplicationStatus.INTERESTED).getId(), "{}")
                .andExpect(status().isBadRequest());
    }

    @Test
    void rejectsDuplicateExplicitOrderWithoutSaving() throws Exception {
        JobApplication application = saveApplication(ApplicationStatus.INTERESTED);
        createStage(application.getId(), "{\"stageType\":\"DOCUMENT\",\"sortOrder\":2}")
                .andExpect(status().isCreated());

        createStage(application.getId(), "{\"stageType\":\"INTERVIEW\",\"sortOrder\":2}")
                .andExpect(status().isConflict());
    }

    @Test
    void listsInOrderAndGetsOnlyFromOwningApplication() throws Exception {
        JobApplication owner = saveApplication(ApplicationStatus.INTERESTED);
        JobApplication other = saveApplication(ApplicationStatus.PLANNED);
        ApplicationStage third = saveStage(owner, StageType.INTERVIEW, 3, "면접");
        ApplicationStage first = saveStage(owner, StageType.DOCUMENT, 1, "서류");

        mockMvc.perform(get("/api/applications/{id}/stages", owner.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(first.getId()))
                .andExpect(jsonPath("$[1].id").value(third.getId()));
        mockMvc.perform(get("/api/applications/{id}/stages/{stageId}", owner.getId(), third.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.label").value("면접"));
        mockMvc.perform(get("/api/applications/{id}/stages/{stageId}", other.getId(), third.getId()))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/applications/{id}/stages/{stageId}", owner.getId(), Long.MAX_VALUE))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/applications/{id}/stages", Long.MAX_VALUE))
                .andExpect(status().isNotFound());
    }

    @Test
    void patchesFieldsIndependentlyWithoutChangingApplicationStatus() throws Exception {
        JobApplication application = saveApplication(ApplicationStatus.SUBMITTED);
        ApplicationStage stage = stageRepository.saveAndFlush(new ApplicationStage(
                application, StageType.INTERVIEW, "1차", 1,
                java.time.LocalDateTime.of(2026, 9, 1, 10, 0), StageResult.PENDING, "기존 메모"));

        mockMvc.perform(patch("/api/applications/{id}/stages/{stageId}", application.getId(), stage.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"scheduledAt\":\"2026-09-03T14:00:00\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.scheduledAt").value("2026-09-03T14:00:00"))
                .andExpect(jsonPath("$.label").value("1차"))
                .andExpect(jsonPath("$.result").value("PENDING"))
                .andExpect(jsonPath("$.memo").value("기존 메모"))
                .andExpect(jsonPath("$.sortOrder").value(1));

        mockMvc.perform(patch("/api/applications/{id}/stages/{stageId}", application.getId(), stage.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"result\":\"PASSED\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.result").value("PASSED"));
        mockMvc.perform(patch("/api/applications/{id}/stages/{stageId}", application.getId(), stage.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"memo\":\"변경 메모\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.memo").value("변경 메모"))
                .andExpect(jsonPath("$.result").value("PASSED"))
                .andExpect(jsonPath("$.stageType").value("INTERVIEW"));

        mockMvc.perform(patch("/api/applications/{id}/stages/{stageId}", application.getId(), stage.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"result\":\"PENDING\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result").value("PENDING"));
        mockMvc.perform(get("/api/applications/{id}/stages/{stageId}", application.getId(), stage.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result").value("PENDING"))
                .andExpect(jsonPath("$.memo").value("변경 메모"));

        assertThat(applicationRepository.findById(application.getId()).orElseThrow().getStatus())
                .isEqualTo(ApplicationStatus.SUBMITTED);
    }

    @Test
    void returnsNotFoundWhenPatchingWrongOwnerOrUnknownStage() throws Exception {
        JobApplication owner = saveApplication(ApplicationStatus.INTERESTED);
        JobApplication other = saveApplication(ApplicationStatus.INTERESTED);
        ApplicationStage stage = saveStage(owner, StageType.DOCUMENT, 1, null);

        patchStage(other.getId(), stage.getId(), "{\"memo\":\"x\"}").andExpect(status().isNotFound());
        patchStage(owner.getId(), Long.MAX_VALUE, "{\"memo\":\"x\"}").andExpect(status().isNotFound());
        patchStage(Long.MAX_VALUE, stage.getId(), "{\"memo\":\"x\"}").andExpect(status().isNotFound());
    }

    @Test
    void deletesOnlyTargetStageAndHandlesUnknownIds() throws Exception {
        JobApplication application = saveApplication(ApplicationStatus.INTERESTED);
        ApplicationStage target = saveStage(application, StageType.DOCUMENT, 1, null);
        ApplicationStage remaining = saveStage(application, StageType.INTERVIEW, 2, null);

        mockMvc.perform(delete("/api/applications/{id}/stages/{stageId}", application.getId(), target.getId()))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/applications/{id}/stages/{stageId}", application.getId(), target.getId()))
                .andExpect(status().isNotFound());
        assertThat(stageRepository.findById(remaining.getId())).isPresent();
        assertThat(applicationRepository.findById(application.getId())).isPresent();
        mockMvc.perform(delete("/api/applications/{id}/stages/{stageId}", application.getId(), Long.MAX_VALUE))
                .andExpect(status().isNotFound());
    }

    private org.springframework.test.web.servlet.ResultActions createStage(Long id, String content) throws Exception {
        return mockMvc.perform(post("/api/applications/{id}/stages", id)
                .contentType(MediaType.APPLICATION_JSON).content(content));
    }

    private org.springframework.test.web.servlet.ResultActions patchStage(Long id, Long stageId, String content)
            throws Exception {
        return mockMvc.perform(patch("/api/applications/{id}/stages/{stageId}", id, stageId)
                .contentType(MediaType.APPLICATION_JSON).content(content));
    }

    private ApplicationStage saveStage(JobApplication application, StageType type, int order, String label) {
        return stageRepository.saveAndFlush(new ApplicationStage(
                application, type, label, order, null, StageResult.PENDING, null));
    }

    private JobApplication saveApplication(ApplicationStatus status) {
        JobPosting posting = postingRepository.saveAndFlush(new JobPosting(
                "기관", "공고", null, "신입", null, "OPEN", null, "정보기술", null,
                null, LocalDate.of(2026, 8, 31), "MANUAL", null,
                java.util.UUID.randomUUID().toString()));
        return applicationRepository.saveAndFlush(new JobApplication(posting, status, null, null));
    }
}
