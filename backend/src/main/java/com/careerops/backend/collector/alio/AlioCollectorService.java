package com.careerops.backend.collector.alio;

import com.careerops.backend.collector.CollectResult;
import com.careerops.backend.job.JobPostingRepository;
import com.careerops.backend.job.JobPostingService;
import com.careerops.backend.job.JobPosting;
import com.careerops.backend.job.dto.JobPostingCreateRequest;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Objects;
import java.util.Set;

@Service
public class AlioCollectorService {

    private static final String METRIC_SOURCE = "alio";
    private static final int MAX_PAGE_SIZE = 1000;
    private static final Logger log = LoggerFactory.getLogger(AlioCollectorService.class);

    private final AlioJobClient client;
    private final JobPostingRepository repository;
    private final JobPostingService jobPostingService;
    private final Validator validator;
    private final MeterRegistry meterRegistry;
    private final Counter fetchedCounter;
    private final Counter savedCounter;
    private final Counter pagesCounter;
    private final AlioDetailEnrichmentService detailEnrichmentService;

    public AlioCollectorService(
            AlioJobClient client,
            JobPostingRepository repository,
            JobPostingService jobPostingService,
            Validator validator,
            MeterRegistry meterRegistry,
            AlioDetailEnrichmentService detailEnrichmentService
    ) {
        this.client = client;
        this.repository = repository;
        this.jobPostingService = jobPostingService;
        this.validator = validator;
        this.meterRegistry = meterRegistry;
        this.detailEnrichmentService = detailEnrichmentService;
        this.fetchedCounter = Counter.builder("careerops.collector.fetched").tag("source", METRIC_SOURCE).register(meterRegistry);
        this.savedCounter = Counter.builder("careerops.collector.saved").tag("source", METRIC_SOURCE).register(meterRegistry);
        this.pagesCounter = Counter.builder("careerops.collector.pages").tag("source", METRIC_SOURCE).register(meterRegistry);
    }

    public CollectResult collect(int numOfRows) {
        int pageSize = Math.min(numOfRows, MAX_PAGE_SIZE);
        int pageNo = 1;
        int maxPages = Integer.MAX_VALUE;
        int fetched = 0;
        int saved = 0;
        int skipped = 0;
        int updated = 0;
        int failed = 0;

        while (fetched < numOfRows) {
            AlioJobListResponse response = fetchPage(pageNo, pageSize);
            pagesCounter.increment();
            if (pageNo == 1) {
                maxPages = (int) Math.ceil((double) response.totalCount() / pageSize) + 5;
            }

            List<AlioJobItem> items = response.result() == null ? List.of() : response.result();
            int remaining = numOfRows - fetched;
            List<AlioJobItem> itemsToProcess = items.size() <= remaining
                    ? items
                    : items.subList(0, remaining);
            fetched += itemsToProcess.size();
            fetchedCounter.increment(itemsToProcess.size());

            for (AlioJobItem item : itemsToProcess) {
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
                var existing = repository.findFirstBySourceAndExternalId(request.source(), request.externalId());
                if (existing.isPresent()) {
                    JobPosting jobPosting = existing.get();
                    if (Objects.equals(jobPosting.getStatus(), request.status())) {
                        skipped++;
                    } else {
                        jobPostingService.updateStatus(jobPosting, request.status());
                        updated++;
                    }
                    enrichIfNeeded(jobPosting);
                    continue;
                }
                JobPosting jobPosting = jobPostingService.create(request);
                saved++;
                savedCounter.increment();
                enrichIfNeeded(jobPosting);
            }

            if (fetched >= numOfRows || items.size() < pageSize) {
                break;
            }
            if (pageNo >= maxPages) {
                log.warn("Stopping ALIO collection at pagination safety limit: pageNo={}, maxPages={}, pageSize={}, fetched={}",
                        pageNo, maxPages, pageSize, fetched);
                break;
            }
            pageNo++;
        }

        runCounter("success").increment();
        return new CollectResult("ALIO", fetched, saved, skipped, updated, failed, "success");
    }

    private AlioJobListResponse fetchPage(int pageNo, int pageSize) {
        try {
            return client.fetchList(pageNo, pageSize);
        } catch (AlioApiException exception) {
            failedCounter(exception.reason().metricTag()).increment();
            runCounter("failed").increment();
            throw exception;
        } catch (RuntimeException exception) {
            failedCounter(AlioApiException.Reason.FETCH_ERROR.metricTag()).increment();
            runCounter("failed").increment();
            throw new AlioApiException(AlioApiException.Reason.FETCH_ERROR, "Failed to fetch ALIO jobs", exception);
        }
    }

    private void enrichIfNeeded(JobPosting jobPosting) {
        if (jobPosting.getDetailFetchedAt() == null) {
            detailEnrichmentService.enrich(jobPosting);
        }
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
