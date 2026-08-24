package com.careerops.backend.notification;

import com.careerops.backend.job.JobPosting;
import com.careerops.backend.job.JobPostingRepository;
import com.careerops.backend.notification.dto.*;
import com.careerops.backend.recommend.JobRecommendationException;
import com.careerops.backend.recommend.JobRecommendationService;
import com.careerops.backend.recommend.dto.JobRecommendation;
import io.micrometer.core.instrument.*;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class NotificationPreparationService {
    private static final Logger log = LoggerFactory.getLogger(NotificationPreparationService.class);
    private final JobRecommendationService recommendationService;
    private final JobRecommendationNotificationRepository repository;
    private final JobPostingRepository jobPostingRepository;
    private final Timer duration;
    private final DistributionSummary createdMetric;
    private final DistributionSummary skippedMetric;
    private final Map<String, Counter> counters = new HashMap<>();

    public NotificationPreparationService(JobRecommendationService recommendationService,
            JobRecommendationNotificationRepository repository, JobPostingRepository jobPostingRepository,
            MeterRegistry meterRegistry) {
        this.recommendationService = recommendationService;
        this.repository = repository;
        this.jobPostingRepository = jobPostingRepository;
        this.duration = Timer.builder("careerops.notification.job-recommendation.duration").register(meterRegistry);
        this.createdMetric = DistributionSummary.builder("careerops.notification.job-recommendation.created").register(meterRegistry);
        this.skippedMetric = DistributionSummary.builder("careerops.notification.job-recommendation.skipped").register(meterRegistry);
        for (String result : List.of("success", "pkb_empty", "provider_error", "validation_failed")) {
            counters.put(result, Counter.builder("careerops.notification.job-recommendation.request")
                    .tag("result", result).register(meterRegistry));
        }
    }

    public NotificationPreparationResponse prepare(int limit) {
        Timer.Sample sample = Timer.start();
        long started = System.nanoTime();
        try {
            List<JobRecommendation> recommendations = recommendationService.recommend(20).recommendations();
            NotificationPreparationResponse response = persist(recommendations, limit);
            counters.get("success").increment();
            createdMetric.record(response.createdCount());
            skippedMetric.record(response.alreadyNotifiedCount());
            log.info("Notification preparation success created={} alreadyNotified={} durationMs={} jobIds={}",
                    response.createdCount(), response.alreadyNotifiedCount(), elapsedMs(started),
                    response.notifications().stream().map(JobRecommendationNotificationResponse::jobId).toList());
            return response;
        } catch (ResponseStatusException exception) {
            if (exception.getStatusCode().value() == 409) counters.get("pkb_empty").increment();
            throw exception;
        } catch (JobRecommendationException exception) {
            counters.get(exception.isValidationFailure() ? "validation_failed" : "provider_error").increment();
            throw exception;
        } finally {
            sample.stop(duration);
        }
    }

    private NotificationPreparationResponse persist(List<JobRecommendation> recommendations, int limit) {
        List<Long> rawIds = recommendations.stream().map(JobRecommendation::jobId).toList();
        Set<Long> existingIds = rawIds.isEmpty() ? Set.of() : new HashSet<>(repository.findExistingJobPostingIds(rawIds));
        List<Long> candidateIds = rawIds.stream().filter(id -> !existingIds.contains(id)).toList();
        Map<Long, JobPosting> openJobs = candidateIds.isEmpty() ? Map.of() : jobPostingRepository.findAllById(candidateIds).stream()
                .filter(job -> "OPEN".equals(job.getStatus()))
                .collect(Collectors.toMap(JobPosting::getId, Function.identity()));
        List<JobRecommendationNotificationResponse> created = new ArrayList<>();
        int alreadyNotified = 0;
        for (JobRecommendation recommendation : recommendations) {
            if (existingIds.contains(recommendation.jobId())) { alreadyNotified++; continue; }
            JobPosting job = openJobs.get(recommendation.jobId());
            if (job == null) continue;
            if (created.size() == limit) break;
            try {
                JobRecommendationNotification saved = repository.save(new JobRecommendationNotification(
                        job, recommendation.recommendationScore(), truncate(recommendation.reason(), 200)));
                created.add(response(saved, job));
            } catch (DataIntegrityViolationException exception) {
                alreadyNotified++;
                log.info("Notification preparation duplicate jobId={}", recommendation.jobId());
            }
        }
        return new NotificationPreparationResponse(created.size(), alreadyNotified, List.copyOf(created));
    }

    public JobRecommendationNotificationListResponse search(NotificationStatus status, Pageable pageable) {
        int size = Math.min(pageable.getPageSize(), 100);
        return JobRecommendationNotificationListResponse.from(
                repository.search(status, PageRequest.of(pageable.getPageNumber(), size, pageable.getSort())));
    }

    private JobRecommendationNotificationResponse response(JobRecommendationNotification value, JobPosting job) {
        return new JobRecommendationNotificationResponse(value.getId(), job.getId(), job.getCompanyName(), job.getTitle(),
                job.getApplicationEndAt(), value.getRecommendationScore(), value.getReason(), value.getStatus(), value.getCreatedAt());
    }

    private String truncate(String value, int max) {
        String text = value == null ? "" : value;
        return text.length() <= max ? text : text.substring(0, max);
    }

    private long elapsedMs(long started) { return (System.nanoTime() - started) / 1_000_000; }
}
