package com.careerops.backend.career;

import com.careerops.backend.career.dto.*;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/career/awards")
public class AwardController {
    private final AwardService service;
    public AwardController(AwardService service) { this.service = service; }
    @PostMapping @ResponseStatus(HttpStatus.CREATED)
    public AwardResponse create(@Valid @RequestBody AwardCreateRequest request) {
        return service.create(request);
    }
    @GetMapping
    public AwardListResponse findAll(@PageableDefault(size = 20) Pageable pageable) {
        return service.findAll(pageable);
    }
    @GetMapping("/{id}") public AwardResponse findById(@PathVariable Long id) { return service.findById(id); }
    @PatchMapping("/{id}") public AwardResponse update(@PathVariable Long id,
            @Valid @RequestBody AwardUpdateRequest request) { return service.update(id, request); }
    @DeleteMapping("/{id}") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) { service.delete(id); }
}
