package com.careerops.backend.match;

import com.careerops.backend.match.dto.SemanticJobMatchResponse;
import com.careerops.backend.match.semantic.SemanticMatchException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/jobs")
public class SemanticJobMatchController {
    private final SemanticJobMatchService service;
    public SemanticJobMatchController(SemanticJobMatchService service) { this.service = service; }

    @PostMapping("/{jobId}/semantic-match")
    public SemanticJobMatchResponse match(@PathVariable Long jobId) { return service.match(jobId); }

    @ExceptionHandler(SemanticMatchException.class)
    @ResponseStatus(HttpStatus.BAD_GATEWAY)
    public void semanticFailure() { }
}
