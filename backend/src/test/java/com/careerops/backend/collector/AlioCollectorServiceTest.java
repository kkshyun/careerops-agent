package com.careerops.backend.collector;

import com.careerops.backend.collector.alio.AlioApiException;
import com.careerops.backend.collector.alio.AlioCollectorService;
import com.careerops.backend.job.JobPosting;
import com.careerops.backend.job.JobPostingRepository;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.annotation.Rollback;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDate;

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

    @Test
    void collectsMapsSavesAndRecordsMetrics() throws Exception {
        client.respondWith(AlioFixtureSupport.read(objectMapper, "alio-list-response-valid.json"));
        long rowsBefore = repository.count();
        double fetchedBefore = counter("careerops.collector.fetched", "source", "alio");
        double savedBefore = counter("careerops.collector.saved", "source", "alio");
        double runBefore = counter("careerops.collector.run", "source", "alio", "result", "success");

        CollectResult result = service.collect(50);

        assertThat(result).isEqualTo(new CollectResult("ALIO", 2, 2, 0, 0, "success"));
        assertThat(repository.count()).isEqualTo(rowsBefore + 2);
        JobPosting first = repository.findAll().stream()
                .filter(job -> "1001".equals(job.getExternalId())).findFirst().orElseThrow();
        assertThat(first.getCompanyName()).isEqualTo("합성 공공기관 A");
        assertThat(first.getTitle()).isEqualTo("2026년 전산직 신입 채용");
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

        assertThat(first).isEqualTo(new CollectResult("ALIO", 3, 1, 0, 2, "success"));
        assertThat(second).isEqualTo(new CollectResult("ALIO", 3, 0, 1, 2, "success"));
        assertThat(repository.count()).isEqualTo(rowsAfterFirst);
        assertThat(counter("careerops.collector.failed", "source", "alio", "reason", "invalid_item"))
                .isEqualTo(invalidBefore + 4);
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

    private double counter(String name, String... tags) {
        return meterRegistry.counter(name, tags).count();
    }
}
