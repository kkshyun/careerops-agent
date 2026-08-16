package com.careerops.backend.job;

import com.careerops.backend.job.dto.JobPostingCreateRequest;
import com.careerops.backend.job.dto.JobPostingDetailResponse;
import com.careerops.backend.job.dto.JobPostingListResponse;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.http.HttpStatus;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class JobPostingService {

    private final JobPostingRepository repository;
    private final RecruitmentStepRepository recruitmentStepRepository;
    private final AttachmentRepository attachmentRepository;
    private final Counter createdCounter;
    private final Counter foundCounter;
    private final Counter notFoundCounter;
    private final Counter conflictCounter;

    public JobPostingService(
            JobPostingRepository repository,
            RecruitmentStepRepository recruitmentStepRepository,
            AttachmentRepository attachmentRepository,
            MeterRegistry meterRegistry) {
        this.repository = repository;
        this.recruitmentStepRepository = recruitmentStepRepository;
        this.attachmentRepository = attachmentRepository;
        this.createdCounter = Counter.builder("careerops.job.creation").register(meterRegistry);
        this.foundCounter = Counter.builder("careerops.job.read").tag("result", "found").register(meterRegistry);
        this.notFoundCounter = Counter.builder("careerops.job.read").tag("result", "not_found").register(meterRegistry);
        this.conflictCounter = Counter.builder("careerops.collector.conflict")
                .tag("source", "alio")
                .register(meterRegistry);
    }

    public JobPosting create(JobPostingCreateRequest request) {
        JobPosting saved = repository.save(new JobPosting(
                request.companyName(), request.title(), request.employmentType(),
                request.careerLevel(), request.educationRequirement(), request.status(), request.institutionCode(),
                request.jobCategory(), request.location(), request.applicationStartAt(),
                request.applicationEndAt(), request.source(), request.sourceUrl(), request.externalId()
        ));
        createdCounter.increment();
        return saved;
    }

    public CreateOutcome createOrGetExisting(JobPostingCreateRequest request) {
        try {
            return new CreateOutcome(create(request), true);
        } catch (DataIntegrityViolationException exception) {
            JobPosting existing = repository
                    .findFirstBySourceAndExternalId(request.source(), request.externalId())
                    .orElseThrow(() -> exception);
            conflictCounter.increment();
            return new CreateOutcome(existing, false);
        }
    }

    public record CreateOutcome(JobPosting jobPosting, boolean isNew) {
    }

    public void updateStatus(JobPosting jobPosting, String status) {
        jobPosting.updateStatus(status);
        repository.save(jobPosting);
    }

    public void markDetailFetched(JobPosting jobPosting, java.time.Instant fetchedAt) {
        jobPosting.markDetailFetched(fetchedAt);
        repository.save(jobPosting);
    }

    public JobPostingDetailResponse findById(Long id) {
        JobPosting jobPosting = repository.findById(id)
                .map(posting -> {
                    foundCounter.increment();
                    return posting;
                })
                .orElseThrow(() -> {
                    notFoundCounter.increment();
                    return new ResponseStatusException(HttpStatus.NOT_FOUND);
                });
        return JobPostingDetailResponse.from(
                jobPosting,
                recruitmentStepRepository.findByJobPostingIdOrderBySortNoAscRecrutStepSnAsc(id),
                attachmentRepository.findByJobPostingIdOrderBySortNoAscRecrutAtchFileNoAsc(id));
    }

    public JobPostingListResponse search(
            String status,
            String careerLevel,
            String companyName,
            String jobCategory,
            Pageable pageable) {
        int clampedSize = Math.min(pageable.getPageSize(), 100);
        PageRequest pageRequest = PageRequest.of(pageable.getPageNumber(), clampedSize);
        return JobPostingListResponse.from(
                repository.search(
                        status,
                        likePattern(careerLevel),
                        likePattern(companyName),
                        likePattern(jobCategory),
                        pageRequest));
    }

    private static String likePattern(String value) {
        return value == null ? null : "%" + value + "%";
    }
}
