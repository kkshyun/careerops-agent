package com.careerops.backend.applicationdraft;

import com.careerops.backend.applicationdraft.dto.*;
import com.careerops.backend.applicationdraft.llm.ApplicationDraftException;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/jobs")
public class ApplicationDraftController {
    private final ApplicationDraftService service;
    public ApplicationDraftController(ApplicationDraftService service){this.service=service;}
    @PostMapping("/{jobId}/application-draft")
    public ApplicationDraftResponse draft(@PathVariable Long jobId,@Valid @RequestBody ApplicationDraftRequest request){return service.draft(jobId,request);}
    @ExceptionHandler(ApplicationDraftException.class) @ResponseStatus(HttpStatus.BAD_GATEWAY)
    public void draftFailure(){}
}
