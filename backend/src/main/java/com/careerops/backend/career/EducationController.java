package com.careerops.backend.career;

import com.careerops.backend.career.dto.*;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/career/educations")
public class EducationController {
    private final EducationService service;
    public EducationController(EducationService service) { this.service = service; }
    @PostMapping @ResponseStatus(HttpStatus.CREATED)
    public EducationResponse create(@Valid @RequestBody EducationCreateRequest request) {
        return service.create(request);
    }
    @GetMapping
    public EducationListResponse findAll(@PageableDefault(size = 20) Pageable pageable) {
        return service.findAll(pageable);
    }
    @GetMapping("/{id}") public EducationResponse findById(@PathVariable Long id) { return service.findById(id); }
    @PatchMapping("/{id}") public EducationResponse update(@PathVariable Long id,
            @Valid @RequestBody EducationUpdateRequest request) { return service.update(id, request); }
    @DeleteMapping("/{id}") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) { service.delete(id); }
}
