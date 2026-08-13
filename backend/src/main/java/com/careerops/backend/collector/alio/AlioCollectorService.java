package com.careerops.backend.collector.alio;

import com.careerops.backend.collector.CollectResult;
import com.careerops.backend.job.JobPostingRepository;
import com.careerops.backend.job.JobPostingService;
import com.careerops.backend.job.dto.JobPostingCreateRequest;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;

@Service
public class AlioCollectorService {

    private static final String METRIC_SOURCE = "alio";

    private final AlioJobClient client;
    private final JobPostingRepository repository;
    private final JobPostingService jobPostingService;
    private final Validator validator;
    private final MeterRegistry meterRegistry;
    private final Counter fetchedCounter;
    private final Counter savedCounter;

    public AlioCollectorService(
            AlioJobClient client,
            JobPostingRepository repository,
            JobPostingService jobPostingService,
            Validator validator,
            MeterRegistry meterRegistry
    ) {
        this.client = client;
        this.repository = repository;
        this.jobPostingService = jobPostingService;
        this.validator = validator;
        this.meterRegistry = meterRegistry;
        this.fetchedCounter = Counter.builder("careerops.collector.fetched").tag("source", METRIC_SOURCE).register(meterRegistry);
        this.savedCounter = Counter.builder("careerops.collector.saved").tag("source", METRIC_SOURCE).register(meterRegistry);
    }

    public CollectResult collect(int numOfRows) {
        AlioJobListResponse response;
        try {
            response = client.fetchList(1, numOfRows);
        } catch (AlioApiException exception) {
            failedCounter(exception.reason().metricTag()).increment();
            runCounter("failed").increment();
            throw exception;
        } catch (RuntimeException exception) {
            failedCounter(AlioApiException.Reason.FETCH_ERROR.metricTag()).increment();
            runCounter("failed").increment();
            throw new AlioApiException(AlioApiException.Reason.FETCH_ERROR, "Failed to fetch ALIO jobs", exception);
        }

        List<AlioJobItem> items = response.result() == null ? List.of() : response.result();
        int fetched = items.size();
        int saved = 0;
        int skipped = 0;
        int failed = 0;
        fetchedCounter.increment(fetched);

        for (AlioJobItem item : items) {
            if (item == null) {
                failed++;
                failedCounter("invalid_item").increment();
                continue;
            }
            JobPostingCreateRequest request = AlioJobMapper.from(item);
            Set<ConstraintViolation<JobPostingCreateRequest>> violations = validator.validate(request);
            if (!violations.isEmpty()) {
                // Preserve source data: do not invent truncation rules for overlong ALIO fields.
                failed++;
                failedCounter("invalid_item").increment();
                continue;
            }
            if (repository.existsBySourceAndExternalId(request.source(), request.externalId())) {
                skipped++;
                continue;
            }
            jobPostingService.create(request);
            saved++;
            savedCounter.increment();
        }

        runCounter("success").increment();
        return new CollectResult("ALIO", fetched, saved, skipped, failed, "success");
    }

    private Counter failedCounter(String reason) {
        return Counter.builder("careerops.collector.failed")
                .tag("source", METRIC_SOURCE).tag("reason", reason).register(meterRegistry);
    }

    private Counter runCounter(String result) {
        return Counter.builder("careerops.collector.run")
                .tag("source", METRIC_SOURCE).tag("result", result).register(meterRegistry);
    }
}
