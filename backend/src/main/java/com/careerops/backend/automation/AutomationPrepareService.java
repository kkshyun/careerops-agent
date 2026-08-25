package com.careerops.backend.automation;

import com.careerops.backend.notification.NotificationPreparationService;
import com.careerops.backend.notification.dto.NotificationPreparationResponse;
import com.careerops.backend.recommend.JobRecommendationException;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AutomationPrepareService {
    private static final Logger log = LoggerFactory.getLogger(AutomationPrepareService.class);

    private final NotificationPreparationService preparationService;
    private final Counter completedCounter;
    private final Counter failedCounter;
    private final Timer durationTimer;
    private final int prepareLimit;

    public AutomationPrepareService(NotificationPreparationService preparationService, MeterRegistry meterRegistry,
            @Value("${careerops.automation.prepare.limit:5}") int prepareLimit) {
        this.preparationService = preparationService;
        this.completedCounter = meterRegistry.counter("careerops.automation.prepare.run", "result", "completed");
        this.failedCounter = meterRegistry.counter("careerops.automation.prepare.run", "result", "failed");
        this.durationTimer = meterRegistry.timer("careerops.automation.prepare.duration");
        this.prepareLimit = prepareLimit;
    }

    public AutomationPrepareRunResult runOnce() {
        long started = System.nanoTime();
        Timer.Sample sample = Timer.start();
        try {
            NotificationPreparationResponse response = preparationService.prepare(prepareLimit);
            completedCounter.increment();
            return result(true, response.createdCount(), response.alreadyNotifiedCount(), started);
        } catch (ResponseStatusException exception) {
            if (exception.getStatusCode().value() != 409) {
                throw exception;
            }
            failedCounter.increment();
            return result(false, 0, 0, started);
        } catch (JobRecommendationException exception) {
            failedCounter.increment();
            return result(false, 0, 0, started);
        } finally {
            sample.stop(durationTimer);
        }
    }

    private AutomationPrepareRunResult result(boolean succeeded, int created, int alreadyNotified, long started) {
        long durationMs = (System.nanoTime() - started) / 1_000_000;
        log.info("Automation prepare completed succeeded={} created={} alreadyNotified={} durationMs={}",
                succeeded, created, alreadyNotified, durationMs);
        return new AutomationPrepareRunResult(succeeded, created, alreadyNotified, durationMs);
    }
}
