package com.careerops.backend.collector;

import com.careerops.backend.collector.alio.*;
import com.careerops.backend.job.*;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Import(AlioTestConfiguration.class)
@Transactional
class AlioDetailEnrichmentServiceTest {
    @Autowired AlioDetailEnrichmentService service;
    @Autowired FixtureAlioJobClient client;
    @Autowired JobPostingService jobPostingService;
    @Autowired JobPostingRepository jobPostingRepository;
    @Autowired RecruitmentStepRepository stepRepository;
    @Autowired AttachmentRepository attachmentRepository;
    @Autowired MeterRegistry meterRegistry;
    @Autowired ObjectMapper objectMapper;

    @BeforeEach void reset() { client.resetDetails(); }

    @Test
    void savesAllFieldsMarksFetchedAndIsIdempotent() throws Exception {
        JobPosting posting = createPosting("1001");
        client.respondToDetail(1001, AlioFixtureSupport.readDetail(objectMapper, "alio-detail-response-valid.json"));
        double stepsBefore = meterRegistry.counter("careerops.collector.detail.steps").count();
        double filesBefore = meterRegistry.counter("careerops.collector.detail.files").count();
        double successBefore = meterRegistry.counter("careerops.collector.detail.run", "result", "success").count();
        long durationBefore = meterRegistry.timer("careerops.collector.detail.duration").count();

        service.enrich(posting);
        service.enrich(posting);

        assertThat(stepRepository.findByJobPostingId(posting.getId())).hasSize(2);
        RecruitmentStep step = stepRepository.findByJobPostingId(posting.getId()).getFirst();
        assertThat(step.getRecrutStepSn()).isEqualTo(51001L);
        assertThat(step.getSortNo()).isZero();
        assertThat(step.getMinStepSn()).isEqualTo(51001L);
        assertThat(step.getMaxStepSn()).isEqualTo(51002L);
        assertThat(step.getStepGroupName()).isEqualTo("정보기술");
        assertThat(step.getCompetitionRate()).isEqualTo(3.5);
        assertThat(step.getApplicantCount()).isEqualTo(14);
        assertThat(step.getRecruitCount()).isEqualTo(4);
        assertThat(step.getOccurredAtRaw()).isEqualTo("합성-원문");
        assertThat(attachmentRepository.findByJobPostingId(posting.getId())).hasSize(3);
        Attachment file = attachmentRepository.findByJobPostingId(posting.getId()).getFirst();
        assertThat(file.getFileName()).isEqualTo("합성 채용공고.pdf");
        assertThat(file.getFileType()).isEqualTo("A");
        assertThat(file.getUrl()).isEqualTo("https://example.invalid/files/61001");
        assertThat(posting.getDetailFetchedAt()).isNotNull();
        assertThat(meterRegistry.counter("careerops.collector.detail.steps").count()).isEqualTo(stepsBefore + 2);
        assertThat(meterRegistry.counter("careerops.collector.detail.files").count()).isEqualTo(filesBefore + 3);
        assertThat(meterRegistry.counter("careerops.collector.detail.run", "result", "success").count()).isEqualTo(successBefore + 2);
        assertThat(meterRegistry.timer("careerops.collector.detail.duration").count()).isEqualTo(durationBefore + 2);
    }

    @Test
    void absorbsFailureWithoutMarkingFetched() {
        JobPosting posting = createPosting("9001");
        client.failDetailWith(9001, new AlioApiException(AlioApiException.Reason.FETCH_ERROR, "synthetic failure"));
        double failedBefore = meterRegistry.counter("careerops.collector.detail.run", "result", "failed").count();

        service.enrich(posting);

        assertThat(stepRepository.findByJobPostingId(posting.getId())).isEmpty();
        assertThat(attachmentRepository.findByJobPostingId(posting.getId())).isEmpty();
        assertThat(posting.getDetailFetchedAt()).isNull();
        assertThat(meterRegistry.counter("careerops.collector.detail.run", "result", "failed").count()).isEqualTo(failedBefore + 1);
    }

    private JobPosting createPosting(String externalId) {
        return jobPostingService.create(new com.careerops.backend.job.dto.JobPostingCreateRequest(
                "합성기관", "합성공고", null, null, null, "OPEN", null, null, null,
                null, null, "ALIO", "https://example.invalid/" + externalId, externalId));
    }
}
