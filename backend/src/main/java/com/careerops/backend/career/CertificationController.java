package com.careerops.backend.career;

import com.careerops.backend.career.dto.*;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/career/certifications")
public class CertificationController {
    private final CertificationService service;
    public CertificationController(CertificationService service) { this.service = service; }
    @PostMapping @ResponseStatus(HttpStatus.CREATED)
    public CertificationResponse create(@Valid @RequestBody CertificationCreateRequest request) {
        return service.create(request);
    }
    @GetMapping
    public CertificationListResponse findAll(@PageableDefault(size = 20) Pageable pageable) {
        return service.findAll(pageable);
    }
    @GetMapping("/{id}") public CertificationResponse findById(@PathVariable Long id) { return service.findById(id); }
    @PatchMapping("/{id}") public CertificationResponse update(@PathVariable Long id,
            @Valid @RequestBody CertificationUpdateRequest request) { return service.update(id, request); }
    @DeleteMapping("/{id}") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) { service.delete(id); }
}
