package com.careerops.backend.match;

import com.careerops.backend.match.dto.JobMatchResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/jobs")
public class JobMatchController {
    private final JobMatchService service;

    public JobMatchController(JobMatchService service) {
        this.service = service;
    }

    @GetMapping("/{jobId}/match")
    public JobMatchResponse match(@PathVariable Long jobId) {
        return service.match(jobId);
    }
}
