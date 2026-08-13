package com.careerops.backend.job;

import com.careerops.backend.job.dto.JobPostingCreateRequest;
import com.careerops.backend.job.dto.JobPostingResponse;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class JobPostingService {

    private final JobPostingRepository repository;
    private final Counter createdCounter;
    private final Counter foundCounter;
    private final Counter notFoundCounter;

    public JobPostingService(JobPostingRepository repository, MeterRegistry meterRegistry) {
        this.repository = repository;
        this.createdCounter = Counter.builder("careerops.job.creation").register(meterRegistry);
        this.foundCounter = Counter.builder("careerops.job.read").tag("result", "found").register(meterRegistry);
        this.notFoundCounter = Counter.builder("careerops.job.read").tag("result", "not_found").register(meterRegistry);
    }

    public JobPostingResponse create(JobPostingCreateRequest request) {
        JobPosting saved = repository.save(new JobPosting(
                request.companyName(), request.title(), request.employmentType(),
                request.jobCategory(), request.location(), request.applicationStartAt(),
                request.applicationEndAt(), request.source(), request.sourceUrl(), request.externalId()
        ));
        createdCounter.increment();
        return JobPostingResponse.from(saved);
    }

    public JobPostingResponse findById(Long id) {
        return repository.findById(id)
                .map(jobPosting -> {
                    foundCounter.increment();
                    return JobPostingResponse.from(jobPosting);
                })
                .orElseThrow(() -> {
                    notFoundCounter.increment();
                    return new ResponseStatusException(HttpStatus.NOT_FOUND);
                });
    }
}
