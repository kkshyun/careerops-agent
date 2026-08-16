package com.careerops.backend.job;

import com.careerops.backend.job.dto.JobPostingCreateRequest;
import com.careerops.backend.job.dto.JobPostingDetailResponse;
import com.careerops.backend.job.dto.JobPostingListResponse;
import com.careerops.backend.job.dto.JobPostingResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/jobs")
public class JobPostingController {

    private final JobPostingService service;

    public JobPostingController(JobPostingService service) {
        this.service = service;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public JobPostingResponse create(@Valid @RequestBody JobPostingCreateRequest request) {
        JobPosting saved = service.create(request);
        return JobPostingResponse.from(saved);
    }

    @GetMapping("/{id}")
    public JobPostingDetailResponse findById(@PathVariable Long id) {
        return service.findById(id);
    }

    @GetMapping
    public JobPostingListResponse search(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String careerLevel,
            @RequestParam(required = false) String companyName,
            @RequestParam(required = false) String jobCategory,
            @PageableDefault(size = 20) Pageable pageable) {
        return service.search(status, careerLevel, companyName, jobCategory, pageable);
    }
}
