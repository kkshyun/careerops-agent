package com.careerops.backend.pkbimport;

import com.careerops.backend.pkbimport.dto.ImportBatchListResponse;
import com.careerops.backend.pkbimport.dto.ImportBatchResponse;
import com.careerops.backend.pkbimport.dto.ExtractionResponse;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/career/imports")
public class ImportBatchController {
    private final ImportBatchService service;
    private final ImportBatchExtractionService extractionService;

    public ImportBatchController(ImportBatchService service, ImportBatchExtractionService extractionService) {
        this.service = service;
        this.extractionService = extractionService;
    }

    @PostMapping("/documents/{documentId}/batches")
    @ResponseStatus(HttpStatus.CREATED)
    public ImportBatchResponse create(@PathVariable Long documentId) {
        return service.create(documentId);
    }

    @GetMapping("/batches")
    public ImportBatchListResponse findAll(@RequestParam(required = false) Long sourceDocumentId,
                                           @PageableDefault(size = 20) Pageable pageable) {
        return service.findAll(sourceDocumentId, pageable);
    }

    @GetMapping("/batches/{id}")
    public ImportBatchResponse findById(@PathVariable Long id) {
        return service.findById(id);
    }

    @PostMapping("/batches/{id}/complete")
    public ImportBatchResponse complete(@PathVariable Long id) {
        return service.complete(id);
    }

    @PostMapping("/batches/{id}/extract")
    public ExtractionResponse extract(@PathVariable Long id) {
        return extractionService.extract(id);
    }
}
