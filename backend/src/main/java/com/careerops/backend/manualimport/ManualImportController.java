package com.careerops.backend.manualimport;

import com.careerops.backend.manualimport.dto.ManualJobImportRequest;
import com.careerops.backend.manualimport.dto.ManualJobImportResult;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/import/jobs/manual")
public class ManualImportController {

    private final ManualImportService service;

    public ManualImportController(ManualImportService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<ManualJobImportResult> importJob(
            @Valid @RequestBody ManualJobImportRequest request
    ) {
        ManualJobImportResult result = service.importJob(request);
        HttpStatus status = "saved".equals(result.result()) ? HttpStatus.CREATED : HttpStatus.OK;
        return ResponseEntity.status(status).body(result);
    }
}
