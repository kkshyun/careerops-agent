package com.careerops.backend.application;

import com.careerops.backend.application.dto.JobApplicationCreateRequest;
import com.careerops.backend.application.dto.JobApplicationListResponse;
import com.careerops.backend.application.dto.JobApplicationResponse;
import com.careerops.backend.application.dto.JobApplicationUpdateRequest;
import com.careerops.backend.job.JobPosting;
import com.careerops.backend.job.JobPostingRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class JobApplicationService {

    private final JobApplicationRepository repository;
    private final JobPostingRepository jobPostingRepository;

    public JobApplicationService(
            JobApplicationRepository repository,
            JobPostingRepository jobPostingRepository) {
        this.repository = repository;
        this.jobPostingRepository = jobPostingRepository;
    }

    public JobApplicationResponse create(JobApplicationCreateRequest request) {
        JobPosting jobPosting = jobPostingRepository.findById(request.jobPostingId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        if (repository.existsByJobPostingId(request.jobPostingId())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT);
        }

        ApplicationStatus status = request.status() == null ? ApplicationStatus.INTERESTED : request.status();
        JobApplication saved;
        try {
            saved = repository.save(new JobApplication(jobPosting, status, request.memo(), request.appliedAt()));
        } catch (DataIntegrityViolationException exception) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Job application already exists", exception);
        }
        return findResponseById(saved.getId());
    }

    public JobApplicationResponse findById(Long id) {
        return findResponseById(id);
    }

    public JobApplicationListResponse search(ApplicationStatus status, Pageable pageable) {
        int clampedSize = Math.min(pageable.getPageSize(), 100);
        PageRequest pageRequest = PageRequest.of(pageable.getPageNumber(), clampedSize);
        return JobApplicationListResponse.from(repository.search(status, pageRequest));
    }

    public JobApplicationResponse update(Long id, JobApplicationUpdateRequest request) {
        JobApplication application = repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        if (request.status() != null) application.updateStatus(request.status());
        if (request.memo() != null) application.updateMemo(request.memo());
        if (request.appliedAt() != null) application.updateAppliedAt(request.appliedAt());
        JobApplication saved = repository.save(application);
        return findResponseById(saved.getId());
    }

    public void delete(Long id) {
        if (!repository.existsById(id)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        repository.deleteById(id);
    }

    private JobApplicationResponse findResponseById(Long id) {
        return repository.findResponseById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }
}
