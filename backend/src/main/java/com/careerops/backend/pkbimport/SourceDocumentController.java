package com.careerops.backend.pkbimport;

import com.careerops.backend.pkbimport.dto.*;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/career/imports/documents")
public class SourceDocumentController {
    private final SourceDocumentService service;

    public SourceDocumentController(SourceDocumentService service) {
        this.service = service;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public SourceDocumentDetailResponse create(@Valid @RequestBody SourceDocumentCreateRequest request) {
        return service.create(request);
    }

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public SourceDocumentDetailResponse upload(@RequestParam MultipartFile file,
                                               @RequestParam DocumentType documentType) {
        return service.createFromUpload(file, documentType);
    }

    @GetMapping
    public SourceDocumentListResponse findAll(@RequestParam(required = false) DocumentType documentType,
                                              @PageableDefault(size = 20) Pageable pageable) {
        return service.findAll(documentType, pageable);
    }

    @GetMapping("/{id}")
    public SourceDocumentDetailResponse findById(@PathVariable Long id) {
        return service.findById(id);
    }
}
