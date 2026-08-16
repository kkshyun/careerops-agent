package com.careerops.backend.collector;

import com.careerops.backend.collector.alio.AlioCollectorService;
import com.careerops.backend.collector.alio.AlioDetailEnrichmentService;
import com.careerops.backend.collector.alio.AlioJobClient;
import com.careerops.backend.collector.alio.AlioJobDetailResponse;
import com.careerops.backend.collector.alio.AlioJobItem;
import com.careerops.backend.collector.alio.AlioJobListResponse;
import com.careerops.backend.job.JobPosting;
import com.careerops.backend.job.JobPostingRepository;
import com.careerops.backend.job.JobPostingService;
import com.careerops.backend.job.dto.JobPostingCreateRequest;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.validation.Validator;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

@SpringBootTest
class AlioCollectorConcurrencyTest {

    private static final String EXTERNAL_ID_PREFIX = "collect-006-";

    @Autowired private JobPostingService jobPostingService;
    @Autowired private JobPostingRepository repository;
    @Autowired private Validator validator;
    @Autowired private MeterRegistry meterRegistry;

    @AfterEach
    void deleteCreatedRows() {
        repository.findAll().stream()
                .filter(posting -> posting.getSourceUrl() != null
                        && posting.getSourceUrl().contains("collect-006"))
                .forEach(repository::delete);
        repository.flush();
    }

    @Test
    void concurrentCreateReturnsOneCanonicalRowAndRecordsOneConflict() throws Exception {
        String externalId = EXTERNAL_ID_PREFIX + UUID.randomUUID();
        JobPostingCreateRequest request = request(externalId);
        CyclicBarrier barrier = new CyclicBarrier(2);
        double conflictsBefore = meterRegistry.counter(
                "careerops.collector.conflict", "source", "alio").count();

        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            Future<JobPostingService.CreateOutcome> first = executor.submit(() -> {
                barrier.await(5, TimeUnit.SECONDS);
                return jobPostingService.createOrGetExisting(request);
            });
            Future<JobPostingService.CreateOutcome> second = executor.submit(() -> {
                barrier.await(5, TimeUnit.SECONDS);
                return jobPostingService.createOrGetExisting(request);
            });

            JobPostingService.CreateOutcome firstOutcome = first.get(10, TimeUnit.SECONDS);
            JobPostingService.CreateOutcome secondOutcome = second.get(10, TimeUnit.SECONDS);

            assertThat(repository.countBySourceAndExternalId("ALIO", externalId)).isOne();
            assertThat(List.of(firstOutcome.isNew(), secondOutcome.isNew()))
                    .containsExactlyInAnyOrder(true, false);
            assertThat(firstOutcome.jobPosting().getId()).isEqualTo(secondOutcome.jobPosting().getId());
        }

        assertThat(meterRegistry.counter("careerops.collector.conflict", "source", "alio").count())
                .isEqualTo(conflictsBefore + 1);
    }

    @Test
    void concurrentCollectorInstancesBothSucceedWithoutDuplicateRows() throws Exception {
        String externalId = EXTERNAL_ID_PREFIX + UUID.randomUUID();
        long sequence = Long.parseLong(externalId.substring(EXTERNAL_ID_PREFIX.length()).replace("-", "").substring(0, 12), 16);
        AlioJobItem item = new AlioJobItem(
                "동시성 기관", "동시성 공고", "https://example.invalid/collect-006/" + sequence, sequence,
                "20260801", "20260831", "신입", "정규직", "학력무관", "Y",
                "C006", "정보기술", "서울");
        CyclicBarrier fetchBarrier = new CyclicBarrier(2);
        AlioJobListResponse response = new AlioJobListResponse(List.of(item), "200", "성공", 1);
        AlioDetailEnrichmentService enrichment = mock(AlioDetailEnrichmentService.class);
        AlioCollectorService firstService = collector(new BarrierClient(fetchBarrier, response), enrichment);
        AlioCollectorService secondService = collector(new BarrierClient(fetchBarrier, response), enrichment);

        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            Future<CollectResult> first = executor.submit(() -> firstService.collect(1));
            Future<CollectResult> second = executor.submit(() -> secondService.collect(1));

            CollectResult firstResult = first.get(10, TimeUnit.SECONDS);
            CollectResult secondResult = second.get(10, TimeUnit.SECONDS);

            assertThat(firstResult.result()).isEqualTo("success");
            assertThat(secondResult.result()).isEqualTo("success");
            assertThat(firstResult.saved() + secondResult.saved()).isOne();
            assertThat(repository.countBySourceAndExternalId("ALIO", String.valueOf(sequence))).isOne();
        }
    }

    private AlioCollectorService collector(AlioJobClient client, AlioDetailEnrichmentService enrichment) {
        return new AlioCollectorService(client, repository, jobPostingService, validator, meterRegistry, enrichment);
    }

    private JobPostingCreateRequest request(String externalId) {
        return new JobPostingCreateRequest(
                "동시성 기관", "동시성 공고", "정규직", "신입", "학력무관", "OPEN",
                "C006", "정보기술", "서울", LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31),
                "ALIO", "https://example.invalid/" + externalId, externalId);
    }

    private record BarrierClient(CyclicBarrier barrier, AlioJobListResponse response) implements AlioJobClient {
        @Override
        public AlioJobListResponse fetchList(int pageNo, int numOfRows) {
            try {
                barrier.await(5, TimeUnit.SECONDS);
                return response;
            } catch (Exception exception) {
                throw new IllegalStateException(exception);
            }
        }

        @Override
        public AlioJobDetailResponse fetchDetail(long sn) {
            throw new UnsupportedOperationException();
        }
    }
}
