package com.careerops.backend.manualimport;

import com.careerops.backend.job.JobPostingRepository;
import com.careerops.backend.job.JobPostingService;
import com.careerops.backend.job.dto.JobPostingCreateRequest;
import com.careerops.backend.job.dto.JobPostingResponse;
import com.careerops.backend.manualimport.dto.ManualJobImportRequest;
import com.careerops.backend.manualimport.dto.ManualJobImportResult;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Service;

@Service
public class ManualImportService {

    private static final String SOURCE = "MANUAL";

    private final JobPostingRepository repository;
    private final JobPostingService jobPostingService;
    private final Counter savedCounter;
    private final Counter duplicateCounter;

    public ManualImportService(
            JobPostingRepository repository,
            JobPostingService jobPostingService,
            MeterRegistry meterRegistry
    ) {
        this.repository = repository;
        this.jobPostingService = jobPostingService;
        this.savedCounter = Counter.builder("careerops.manual.import")
                .tag("result", "saved")
                .register(meterRegistry);
        this.duplicateCounter = Counter.builder("careerops.manual.import")
                .tag("result", "duplicate")
                .register(meterRegistry);
    }

    public ManualJobImportResult importJob(ManualJobImportRequest request) {
        return repository.findFirstBySourceAndSourceUrl(SOURCE, request.sourceUrl())
                .map(existing -> {
                    duplicateCounter.increment();
                    return new ManualJobImportResult("duplicate", JobPostingResponse.from(existing));
                })
                .orElseGet(() -> {
                    JobPostingResponse saved = jobPostingService.create(toCreateRequest(request));
                    savedCounter.increment();
                    return new ManualJobImportResult("saved", saved);
                });
    }

    private JobPostingCreateRequest toCreateRequest(ManualJobImportRequest request) {
        return new JobPostingCreateRequest(
                request.companyName(),
                request.title(),
                request.employmentType(),
                null,
                null,
                null,
                null,
                request.jobCategory(),
                request.location(),
                request.applicationStartAt(),
                request.applicationEndAt(),
                SOURCE,
                request.sourceUrl(),
                null
        );
    }
}
