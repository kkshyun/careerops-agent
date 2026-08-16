package com.careerops.backend.collector;

import com.careerops.backend.collector.alio.AlioApiException;
import com.careerops.backend.collector.alio.AlioCollectorService;
import com.careerops.backend.collector.alio.AlioCollectionInProgressException;
import com.careerops.backend.collector.alio.AlioJobListResponse;
import com.careerops.backend.job.JobPosting;
import com.careerops.backend.job.JobPostingRepository;
import com.careerops.backend.job.RecruitmentStepRepository;
import com.careerops.backend.job.AttachmentRepository;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.annotation.Rollback;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Import(AlioTestConfiguration.class)
@Transactional
@Rollback
class AlioCollectorServiceTest {

    @Autowired private AlioCollectorService service;
    @Autowired private FixtureAlioJobClient client;
    @Autowired private JobPostingRepository repository;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private MeterRegistry meterRegistry;
    @Autowired private RecruitmentStepRepository stepRepository;
    @Autowired private AttachmentRepository attachmentRepository;

    @BeforeEach
    void registerDetailFixtures() throws Exception {
        client.resetList();
        client.resetDetails();
        var detail = AlioFixtureSupport.readDetail(objectMapper, "alio-detail-response-empty.json");
        for (long sn : new long[]{1001, 1002, 2001, 2002, 2003}) client.respondToDetail(sn, detail);
    }

    @Test
    void collectsMapsSavesAndRecordsMetrics() throws Exception {
        client.respondWith(AlioFixtureSupport.read(objectMapper, "alio-list-response-valid.json"));
        long rowsBefore = repository.count();
        double fetchedBefore = counter("careerops.collector.fetched", "source", "alio");
        double savedBefore = counter("careerops.collector.saved", "source", "alio");
        double runBefore = counter("careerops.collector.run", "source", "alio", "result", "success");

        CollectResult result = service.collect(50);

        assertThat(result).isEqualTo(new CollectResult("ALIO", 2, 2, 0, 0, 0, "success"));
        assertThat(repository.count()).isEqualTo(rowsBefore + 2);
        JobPosting first = repository.findAll().stream()
                .filter(job -> "1001".equals(job.getExternalId())).findFirst().orElseThrow();
        assertThat(first.getCompanyName()).isEqualTo("합성 공공기관 A");
        assertThat(first.getTitle()).isEqualTo("2026년 전산직 신입 채용");
        assertThat(first.getEmploymentType()).isEqualTo("정규직");
        assertThat(first.getCareerLevel()).isEqualTo("신입");
        assertThat(first.getEducationRequirement()).isEqualTo("학력무관");
        assertThat(first.getStatus()).isEqualTo("OPEN");
        assertThat(first.getInstitutionCode()).isEqualTo("C0059");
        assertThat(first.getSource()).isEqualTo("ALIO");
        assertThat(first.getSourceUrl()).isEqualTo("https://example.invalid/alio/1001");
        assertThat(first.getApplicationStartAt()).isEqualTo(LocalDate.of(2026, 8, 1));
        assertThat(first.getApplicationEndAt()).isEqualTo(LocalDate.of(2026, 8, 20));
        assertThat(counter("careerops.collector.fetched", "source", "alio")).isEqualTo(fetchedBefore + 2);
        assertThat(counter("careerops.collector.saved", "source", "alio")).isEqualTo(savedBefore + 2);
        assertThat(counter("careerops.collector.run", "source", "alio", "result", "success")).isEqualTo(runBefore + 1);
    }

    @Test
    void skipsInvalidItemsAndDuplicates() throws Exception {
        client.respondWith(AlioFixtureSupport.read(objectMapper, "alio-list-response-with-invalid-item.json"));
        double invalidBefore = counter("careerops.collector.failed", "source", "alio", "reason", "invalid_item");

        CollectResult first = service.collect(50);
        long rowsAfterFirst = repository.count();
        CollectResult second = service.collect(50);

        assertThat(first).isEqualTo(new CollectResult("ALIO", 3, 1, 0, 0, 2, "success"));
        assertThat(second).isEqualTo(new CollectResult("ALIO", 3, 0, 1, 0, 2, "success"));
        assertThat(repository.count()).isEqualTo(rowsAfterFirst);
        assertThat(counter("careerops.collector.failed", "source", "alio", "reason", "invalid_item"))
                .isEqualTo(invalidBefore + 4);
    }

    @Test
    void updatesOnlyStatusWhenExistingPostingStatusChanges() throws Exception {
        client.respondWith(AlioFixtureSupport.read(objectMapper, "alio-list-response-valid.json"));
        service.collect(50);
        JobPosting before = repository.findFirstBySourceAndExternalId("ALIO", "1001").orElseThrow();
        long rowsBefore = repository.count();
        String originalCompanyName = before.getCompanyName();
        String originalTitle = before.getTitle();

        client.respondWith(AlioFixtureSupport.read(objectMapper, "alio-list-response-closed.json"));
        CollectResult result = service.collect(50);
        JobPosting after = repository.findFirstBySourceAndExternalId("ALIO", "1001").orElseThrow();

        assertThat(result).isEqualTo(new CollectResult("ALIO", 1, 0, 0, 1, 0, "success"));
        assertThat(repository.count()).isEqualTo(rowsBefore);
        assertThat(after.getStatus()).isEqualTo("CLOSED");
        assertThat(after.getCompanyName()).isEqualTo(originalCompanyName);
        assertThat(after.getTitle()).isEqualTo(originalTitle);
    }

    @Test
    void recordsFailedRunWhenClientFails() {
        client.failWith(new AlioApiException(AlioApiException.Reason.FETCH_ERROR, "fixture failure"));
        long rowsBefore = repository.count();
        double runBefore = counter("careerops.collector.run", "source", "alio", "result", "failed");

        assertThatThrownBy(() -> service.collect(50)).isInstanceOf(AlioApiException.class);

        assertThat(repository.count()).isEqualTo(rowsBefore);
        assertThat(counter("careerops.collector.run", "source", "alio", "result", "failed"))
                .isEqualTo(runBefore + 1);
    }

    @Test
    void rejectsConcurrentRunImmediatelyAndUnlocksAfterCompletion() throws Exception {
        client.respondWith(new AlioJobListResponse(List.of(), "200", "성공", 0));
        client.blockNextFetch();
        double skippedBefore = counter(
                "careerops.collector.run", "source", "alio", "result", "skipped_locked");

        try (ExecutorService executor = Executors.newSingleThreadExecutor()) {
            Future<CollectResult> running = executor.submit(() -> service.collect(1));
            assertThat(client.awaitFetchEntered()).isTrue();

            long startedAt = System.nanoTime();
            assertThatThrownBy(() -> service.collect(1))
                    .isInstanceOf(AlioCollectionInProgressException.class);
            assertThat(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt)).isLessThan(1000);

            client.releaseBlockedFetch();
            assertThat(running.get(5, TimeUnit.SECONDS).result()).isEqualTo("success");
        } finally {
            client.releaseBlockedFetch();
        }

        client.respondWith(new AlioJobListResponse(List.of(), "200", "성공", 0));
        assertThat(service.collect(1).result()).isEqualTo("success");
        assertThat(counter("careerops.collector.run", "source", "alio", "result", "skipped_locked"))
                .isEqualTo(skippedBefore + 1);
    }

    @Test
    void enrichesNewPostingOnceAndDoesNotRefetchWhenRediscovered() throws Exception {
        var list = AlioFixtureSupport.read(objectMapper, "alio-list-response-valid.json");
        client.respondWith(list);
        client.respondToDetail(1001, AlioFixtureSupport.readDetail(objectMapper, "alio-detail-response-valid.json"));

        service.collect(50);
        JobPosting posting = repository.findFirstBySourceAndExternalId("ALIO", "1001").orElseThrow();
        assertThat(stepRepository.findByJobPostingId(posting.getId())).hasSize(2);
        assertThat(attachmentRepository.findByJobPostingId(posting.getId())).hasSize(3);
        assertThat(posting.getDetailFetchedAt()).isNotNull();
        assertThat(client.capturedDetailSns()).containsExactly(1001L, 1002L);

        service.collect(50);
        assertThat(client.capturedDetailSns()).containsExactly(1001L, 1002L);
        assertThat(stepRepository.findByJobPostingId(posting.getId())).hasSize(2);
        assertThat(attachmentRepository.findByJobPostingId(posting.getId())).hasSize(3);
    }

    @Test
    void isolatesOneDetailFailureWhileSavingAllListItems() throws Exception {
        client.respondWith(AlioFixtureSupport.read(objectMapper, "alio-list-response-valid.json"));
        client.respondToDetail(1001, AlioFixtureSupport.readDetail(objectMapper, "alio-detail-response-valid.json"));
        client.failDetailWith(1002, new AlioApiException(AlioApiException.Reason.FETCH_ERROR, "synthetic failure"));

        CollectResult result = service.collect(50);

        assertThat(result.saved()).isEqualTo(2);
        JobPosting successful = repository.findFirstBySourceAndExternalId("ALIO", "1001").orElseThrow();
        JobPosting failed = repository.findFirstBySourceAndExternalId("ALIO", "1002").orElseThrow();
        assertThat(successful.getDetailFetchedAt()).isNotNull();
        assertThat(failed.getDetailFetchedAt()).isNull();
        assertThat(stepRepository.findByJobPostingId(failed.getId())).isEmpty();
        assertThat(attachmentRepository.findByJobPostingId(failed.getId())).isEmpty();
    }

    @Test
    void enrichesPreviouslyUnfetchedPostingDuringStatusUpdate() throws Exception {
        var open = AlioFixtureSupport.read(objectMapper, "alio-list-response-valid.json").result().getFirst();
        JobPosting posting = new com.careerops.backend.job.JobPosting(
                open.instNm(), open.recrutPbancTtl(), open.hireTypeNmLst(), open.recrutSeNm(),
                open.acbgCondNmLst(), "OPEN", open.pblntInstCd(), open.ncsCdNmLst(), open.workRgnNmLst(),
                LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 20), "ALIO", open.srcUrl(), "1001");
        repository.save(posting);
        client.respondWith(AlioFixtureSupport.read(objectMapper, "alio-list-response-closed.json"));
        client.respondToDetail(1001, AlioFixtureSupport.readDetail(objectMapper, "alio-detail-response-valid.json"));

        CollectResult result = service.collect(50);

        assertThat(result.updated()).isOne();
        assertThat(posting.getStatus()).isEqualTo("CLOSED");
        assertThat(posting.getDetailFetchedAt()).isNotNull();
        assertThat(stepRepository.findByJobPostingId(posting.getId())).hasSize(2);
    }

    @Test
    void paginatesWithFixedPageSizeSlicesAtCallerLimitAndRecordsPages() throws Exception {
        var item = AlioFixtureSupport.read(objectMapper, "alio-list-page-1.json").result().getFirst();
        var fullPage = response(Collections.nCopies(1000, item), 4000);
        client.respondToPage(1, fullPage);
        client.respondToPage(2, fullPage);
        client.respondToPage(3, fullPage);
        double pagesBefore = counter("careerops.collector.pages", "source", "alio");

        CollectResult result = service.collect(2500);

        assertThat(result.fetched()).isEqualTo(2500);
        assertThat(result.saved()).isOne();
        assertThat(result.skipped()).isEqualTo(2499);
        assertThat(client.capturedCalls()).containsExactly(
                new FixtureAlioJobClient.ListCall(1, 1000),
                new FixtureAlioJobClient.ListCall(2, 1000),
                new FixtureAlioJobClient.ListCall(3, 1000));
        assertThat(counter("careerops.collector.pages", "source", "alio")).isEqualTo(pagesBefore + 3);
    }

    @Test
    void stopsAfterPartialPageWithoutRequestingAnotherPage() throws Exception {
        var first = AlioFixtureSupport.read(objectMapper, "alio-list-page-1.json").result().getFirst();
        var second = AlioFixtureSupport.read(objectMapper, "alio-list-page-2-partial.json").result().getFirst();
        client.respondToPage(1, response(Collections.nCopies(1000, first), 2000));
        client.respondToPage(2, response(List.of(second), 2000));

        CollectResult result = service.collect(3000);

        assertThat(result.fetched()).isEqualTo(1001);
        assertThat(repository.findFirstBySourceAndExternalId("ALIO", "2001").orElseThrow().getDetailFetchedAt())
                .isNotNull();
        assertThat(repository.findFirstBySourceAndExternalId("ALIO", "2002").orElseThrow().getDetailFetchedAt())
                .isNotNull();
        assertThat(client.capturedDetailSns()).containsExactlyInAnyOrder(2001L, 2002L);
        assertThat(client.capturedCalls()).extracting(FixtureAlioJobClient.ListCall::pageNo)
                .containsExactly(1, 2);
    }

    @Test
    void stopsAtEmptyPageAndKeepsEarlierResults() throws Exception {
        var item = AlioFixtureSupport.read(objectMapper, "alio-list-page-1.json").result().getFirst();
        client.respondToPage(1, response(Collections.nCopies(1000, item), 2000));
        client.respondToPage(2, AlioFixtureSupport.read(objectMapper, "alio-list-page-empty.json"));

        CollectResult result = service.collect(3000);

        assertThat(result.fetched()).isEqualTo(1000);
        assertThat(repository.findFirstBySourceAndExternalId("ALIO", "2001")).isPresent();
        assertThat(client.capturedCalls()).extracting(FixtureAlioJobClient.ListCall::pageNo)
                .containsExactly(1, 2);
    }

    @Test
    void propagatesMiddlePageFailureKeepsCommittedPageWorkAndDoesNotRecordSuccess() throws Exception {
        var first = AlioFixtureSupport.read(objectMapper, "alio-list-page-1.json").result().getFirst();
        var second = AlioFixtureSupport.read(objectMapper, "alio-list-page-2-partial.json").result().getFirst();
        client.respondToPage(1, response(Collections.nCopies(1000, first), 4000));
        client.respondToPage(2, response(Collections.nCopies(1000, second), 4000));
        client.failPageWith(3, new AlioApiException(AlioApiException.Reason.FETCH_ERROR, "page 3 failed"));
        long rowsBefore = repository.count();
        double failedBefore = counter("careerops.collector.run", "source", "alio", "result", "failed");
        double successBefore = counter("careerops.collector.run", "source", "alio", "result", "success");

        assertThatThrownBy(() -> service.collect(4000)).isInstanceOf(AlioApiException.class);

        assertThat(repository.count()).isEqualTo(rowsBefore + 2);
        assertThat(counter("careerops.collector.run", "source", "alio", "result", "failed"))
                .isEqualTo(failedBefore + 1);
        assertThat(counter("careerops.collector.run", "source", "alio", "result", "success"))
                .isEqualTo(successBefore);
        assertThat(client.capturedCalls()).extracting(FixtureAlioJobClient.ListCall::pageNo)
                .containsExactly(1, 2, 3);
    }

    @Test
    void repeatedMultiPageCollectionDoesNotCreateDuplicatesOrRefetchDetails() throws Exception {
        var first = AlioFixtureSupport.read(objectMapper, "alio-list-page-1.json").result().getFirst();
        var second = AlioFixtureSupport.read(objectMapper, "alio-list-page-2-partial.json").result().getFirst();
        client.respondToPage(1, response(Collections.nCopies(1000, first), 1001));
        client.respondToPage(2, response(List.of(second), 1001));

        service.collect(3000);
        long rowsAfterFirst = repository.count();
        List<Long> detailCallsAfterFirst = client.capturedDetailSns();
        client.resetList();
        client.respondToPage(1, response(Collections.nCopies(1000, first), 1001));
        client.respondToPage(2, response(List.of(second), 1001));
        service.collect(3000);

        assertThat(repository.count()).isEqualTo(rowsAfterFirst);
        assertThat(client.capturedDetailSns()).isEqualTo(detailCallsAfterFirst);
    }

    @Test
    void updatesExistingStatusWhenPostingAppearsOnLaterPage() throws Exception {
        var open = AlioFixtureSupport.read(objectMapper, "alio-list-response-valid.json").result().getFirst();
        JobPosting posting = new JobPosting(
                open.instNm(), open.recrutPbancTtl(), open.hireTypeNmLst(), open.recrutSeNm(),
                open.acbgCondNmLst(), "OPEN", open.pblntInstCd(), open.ncsCdNmLst(), open.workRgnNmLst(),
                LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 20), "ALIO", open.srcUrl(), "1001");
        repository.save(posting);
        var filler = AlioFixtureSupport.read(objectMapper, "alio-list-page-1.json").result().getFirst();
        var closed = AlioFixtureSupport.read(objectMapper, "alio-list-response-closed.json").result().getFirst();
        client.respondToPage(1, response(Collections.nCopies(1000, filler), 1001));
        client.respondToPage(2, response(List.of(closed), 1001));

        CollectResult result = service.collect(3000);

        assertThat(result.updated()).isOne();
        assertThat(posting.getStatus()).isEqualTo("CLOSED");
        assertThat(client.capturedCalls()).extracting(FixtureAlioJobClient.ListCall::pageNo)
                .containsExactly(1, 2);
    }

    @Test
    void keepsSinglePageCallBackwardCompatible() throws Exception {
        client.respondWith(AlioFixtureSupport.read(objectMapper, "alio-list-response-valid.json"));

        service.collect(50);

        assertThat(client.capturedCalls()).containsExactly(new FixtureAlioJobClient.ListCall(1, 50));
    }

    private AlioJobListResponse response(List<com.careerops.backend.collector.alio.AlioJobItem> items, int totalCount) {
        return new AlioJobListResponse(items, "200", "성공했습니다.", totalCount);
    }

    private double counter(String name, String... tags) {
        return meterRegistry.counter(name, tags).count();
    }
}
