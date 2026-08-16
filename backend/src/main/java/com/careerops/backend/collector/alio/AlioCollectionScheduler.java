package com.careerops.backend.collector.alio;

import com.careerops.backend.collector.CollectResult;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "careerops.scheduler.alio", name = "enabled", matchIfMissing = true)
public class AlioCollectionScheduler {

    private static final Logger log = LoggerFactory.getLogger(AlioCollectionScheduler.class);

    private final AlioCollectorService alioCollectorService;
    private final MeterRegistry meterRegistry;
    private final Timer durationTimer;
    private final int numOfRows;

    public AlioCollectionScheduler(
            AlioCollectorService alioCollectorService,
            MeterRegistry meterRegistry,
            @Value("${careerops.scheduler.alio.num-of-rows:50}") int numOfRows
    ) {
        this.alioCollectorService = alioCollectorService;
        this.meterRegistry = meterRegistry;
        this.durationTimer = Timer.builder("careerops.scheduler.alio.duration").register(meterRegistry);
        this.numOfRows = numOfRows;
    }

    @Scheduled(
            initialDelayString = "${careerops.scheduler.alio.initial-delay:PT1M}",
            fixedDelayString = "${careerops.scheduler.alio.fixed-delay:PT6H}"
    )
    public void collect() {
        Timer.Sample sample = Timer.start(meterRegistry);
        try {
            CollectResult result = alioCollectorService.collect(numOfRows);
            counter("fetched").increment(result.fetched());
            counter("saved").increment(result.saved());
            counter("skipped").increment(result.skipped());
            counter("updated").increment(result.updated());
            counter("failed").increment(result.failed());
            runCounter("success").increment();
        } catch (AlioCollectionInProgressException exception) {
            runCounter("skipped").increment();
            log.info("Scheduled ALIO collection skipped because another collection is in progress");
        } catch (AlioApiException exception) {
            runCounter("failure").increment();
            log.warn("Scheduled ALIO collection failed: reason={}", exception.reason(), exception);
        } catch (RuntimeException exception) {
            runCounter("failure").increment();
            log.warn("Scheduled ALIO collection failed unexpectedly", exception);
        } finally {
            sample.stop(durationTimer);
        }
    }

    private Counter counter(String metric) {
        return Counter.builder("careerops.scheduler.alio." + metric).register(meterRegistry);
    }

    private Counter runCounter(String result) {
        return Counter.builder("careerops.scheduler.alio.run")
                .tag("result", result)
                .register(meterRegistry);
    }
}
