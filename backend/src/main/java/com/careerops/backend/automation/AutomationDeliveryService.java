package com.careerops.backend.automation;

import com.careerops.backend.notification.JobRecommendationNotificationRepository;
import com.careerops.backend.notification.KakaoDeliveryException;
import com.careerops.backend.notification.NotificationSendService;
import com.careerops.backend.notification.NotificationStatus;
import com.careerops.backend.notification.kakao.KakaoApiException;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
public class AutomationDeliveryService {
    private static final Logger log = LoggerFactory.getLogger(AutomationDeliveryService.class);

    private final JobRecommendationNotificationRepository repository;
    private final NotificationSendService sendService;
    private final Counter completedCounter;
    private final Counter shortCircuitedCounter;
    private final Timer durationTimer;
    private final DistributionSummary candidatesSummary;
    private final int deliveryLimit;

    public AutomationDeliveryService(JobRecommendationNotificationRepository repository,
            NotificationSendService sendService, MeterRegistry meterRegistry,
            @Value("${careerops.automation.delivery.limit:5}") int deliveryLimit) {
        this.repository = repository;
        this.sendService = sendService;
        this.completedCounter = meterRegistry.counter("careerops.automation.delivery.run", "result", "completed");
        this.shortCircuitedCounter = meterRegistry.counter(
                "careerops.automation.delivery.short_circuited", "reason", "token_refresh_failed");
        this.durationTimer = meterRegistry.timer("careerops.automation.delivery.duration");
        this.candidatesSummary = meterRegistry.summary("careerops.automation.delivery.candidates");
        this.deliveryLimit = deliveryLimit;
    }

    public AutomationDeliveryRunResult runOnce() {
        long started = System.nanoTime();
        Timer.Sample sample = Timer.start();
        int attempted = 0;
        int sent = 0;
        int failed = 0;
        boolean shortCircuited = false;
        try {
            List<Long> ids = repository.findIdsByStatusOrderByCreatedAtAsc(
                    NotificationStatus.PENDING, PageRequest.of(0, deliveryLimit));
            candidatesSummary.record(ids.size());
            for (Long id : ids) {
                attempted++;
                try {
                    sendService.send(id);
                    sent++;
                } catch (ResponseStatusException exception) {
                    // A concurrent path may already have handled or removed this notification.
                    int status = exception.getStatusCode().value();
                    if (status != 404 && status != 409) {
                        throw exception;
                    }
                } catch (KakaoDeliveryException exception) {
                    failed++;
                    if (exception.getCause() instanceof KakaoApiException apiException
                            && apiException.reason() == KakaoApiException.Reason.TOKEN_REFRESH_FAILED) {
                        shortCircuited = true;
                        shortCircuitedCounter.increment();
                        break;
                    }
                }
            }
            completedCounter.increment();
            return result(attempted, sent, failed, shortCircuited, started);
        } finally {
            sample.stop(durationTimer);
        }
    }

    private AutomationDeliveryRunResult result(int attempted, int sent, int failed,
            boolean shortCircuited, long started) {
        long durationMs = (System.nanoTime() - started) / 1_000_000;
        log.info("Automation delivery completed attempted={} sent={} failed={} shortCircuited={} durationMs={}",
                attempted, sent, failed, shortCircuited, durationMs);
        return new AutomationDeliveryRunResult(attempted, sent, failed, shortCircuited, durationMs);
    }
}
