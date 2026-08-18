package com.careerops.backend.career;

import com.careerops.backend.career.dto.*;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/career/experiences")
public class CareerExperienceController {
    private final CareerExperienceService service;
    public CareerExperienceController(CareerExperienceService service) { this.service = service; }
    @PostMapping @ResponseStatus(HttpStatus.CREATED)
    public CareerExperienceDetailResponse create(@Valid @RequestBody CareerExperienceCreateRequest request) {
        return service.create(request);
    }
    @GetMapping
    public CareerExperienceListResponse findAll(@RequestParam(required = false) ExperienceType type,
                                                @RequestParam(required = false) String keyword,
                                                @PageableDefault(size = 20) Pageable pageable) {
        return service.findAll(type, keyword, pageable);
    }
    @GetMapping("/{id}") public CareerExperienceDetailResponse findById(@PathVariable Long id) { return service.findById(id); }
    @PatchMapping("/{id}") public CareerExperienceDetailResponse update(@PathVariable Long id,
            @Valid @RequestBody CareerExperienceUpdateRequest request) { return service.update(id, request); }
    @DeleteMapping("/{id}") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) { service.delete(id); }
}
