package com.careerops.backend.collector;

import com.careerops.backend.collector.alio.AlioApiException;
import com.careerops.backend.collector.alio.AlioCollectionScheduler;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.annotation.Rollback;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

@SpringBootTest
@Import(AlioTestConfiguration.class)
@TestPropertySource(properties = {
        "careerops.scheduler.alio.enabled=true",
        "careerops.scheduler.alio.initial-delay=PT24H",
        "careerops.scheduler.alio.num-of-rows=10"
})
@Transactional
@Rollback
class AlioCollectionSchedulerTest {

    @Autowired private AlioCollectionScheduler scheduler;
    @Autowired private FixtureAlioJobClient client;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private MeterRegistry meterRegistry;

    @Test
    void collectsAndRecordsSuccessfulRunMetrics() throws Exception {
        client.respondWith(AlioFixtureSupport.read(objectMapper, "alio-list-response-valid.json"));
        double successBefore = counter("careerops.scheduler.alio.run", "result", "success");
        long durationBefore = meterRegistry.timer("careerops.scheduler.alio.duration").count();
        double fetchedBefore = counter("careerops.scheduler.alio.fetched");
        double savedBefore = counter("careerops.scheduler.alio.saved");
        double skippedBefore = counter("careerops.scheduler.alio.skipped");
        double updatedBefore = counter("careerops.scheduler.alio.updated");
        double failedBefore = counter("careerops.scheduler.alio.failed");

        assertThatCode(scheduler::collect).doesNotThrowAnyException();

        assertThat(counter("careerops.scheduler.alio.run", "result", "success")).isEqualTo(successBefore + 1);
        assertThat(meterRegistry.timer("careerops.scheduler.alio.duration").count()).isEqualTo(durationBefore + 1);
        assertThat(counter("careerops.scheduler.alio.fetched")).isEqualTo(fetchedBefore + 2);
        assertThat(counter("careerops.scheduler.alio.saved")).isEqualTo(savedBefore + 2);
        assertThat(counter("careerops.scheduler.alio.skipped")).isEqualTo(skippedBefore);
        assertThat(counter("careerops.scheduler.alio.updated")).isEqualTo(updatedBefore);
        assertThat(counter("careerops.scheduler.alio.failed")).isEqualTo(failedBefore);
    }

    @Test
    void swallowsFetchFailureAndRecordsFailedRun() {
        client.failWith(new AlioApiException(AlioApiException.Reason.FETCH_ERROR, "fixture failure"));
        double failureBefore = counter("careerops.scheduler.alio.run", "result", "failure");

        assertThatCode(scheduler::collect).doesNotThrowAnyException();

        assertThat(counter("careerops.scheduler.alio.run", "result", "failure")).isEqualTo(failureBefore + 1);
    }

    @Test
    void accumulatesMetricsAcrossConsecutiveRuns() throws Exception {
        double successBefore = counter("careerops.scheduler.alio.run", "result", "success");
        double fetchedBefore = counter("careerops.scheduler.alio.fetched");
        double savedBefore = counter("careerops.scheduler.alio.saved");
        double updatedBefore = counter("careerops.scheduler.alio.updated");

        client.respondWith(AlioFixtureSupport.read(objectMapper, "alio-list-response-valid.json"));
        scheduler.collect();
        client.respondWith(AlioFixtureSupport.read(objectMapper, "alio-list-response-closed.json"));
        scheduler.collect();

        assertThat(counter("careerops.scheduler.alio.run", "result", "success")).isEqualTo(successBefore + 2);
        assertThat(counter("careerops.scheduler.alio.fetched")).isEqualTo(fetchedBefore + 3);
        assertThat(counter("careerops.scheduler.alio.saved")).isEqualTo(savedBefore + 2);
        assertThat(counter("careerops.scheduler.alio.updated")).isEqualTo(updatedBefore + 1);
    }

    @Test
    void passesConfiguredNumOfRowsToCollector() throws Exception {
        client.respondWith(AlioFixtureSupport.read(objectMapper, "alio-list-response-valid.json"));

        scheduler.collect();

        assertThat(client.lastNumOfRows()).isEqualTo(10);
    }

    private double counter(String name, String... tags) {
        return meterRegistry.counter(name, tags).count();
    }
}
