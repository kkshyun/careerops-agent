package com.careerops.backend.pkbimport;

import com.careerops.backend.pkbimport.dto.*;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/career/imports/batches/{batchId}/candidates")
public class ImportCandidateController {
    private final ImportCandidateService service;
    public ImportCandidateController(ImportCandidateService service) { this.service = service; }

    @PostMapping @ResponseStatus(HttpStatus.CREATED)
    public ImportCandidateResponse create(@PathVariable Long batchId,
                                          @Valid @RequestBody ImportCandidateCreateRequest request) {
        return service.create(batchId, request);
    }
    @GetMapping
    public ImportCandidateListResponse findAll(@PathVariable Long batchId,
                                               @RequestParam(required = false) ImportCandidateStatus status,
                                               @PageableDefault(size = 20) Pageable pageable) {
        return service.findAll(batchId, status, pageable);
    }
    @GetMapping("/{candidateId}")
    public ImportCandidateResponse findById(@PathVariable Long batchId, @PathVariable Long candidateId) {
        return service.findById(batchId, candidateId);
    }
    @PostMapping("/{candidateId}/approve")
    public ImportCandidateResponse approve(@PathVariable Long batchId, @PathVariable Long candidateId) {
        return service.approve(batchId, candidateId);
    }
    @PostMapping("/{candidateId}/reject")
    public ImportCandidateResponse reject(@PathVariable Long batchId, @PathVariable Long candidateId) {
        return service.reject(batchId, candidateId);
    }
}
