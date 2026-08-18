package com.careerops.backend.application;

import com.careerops.backend.application.dto.ApplicationStageCreateRequest;
import com.careerops.backend.application.dto.ApplicationStageResponse;
import com.careerops.backend.application.dto.ApplicationStageUpdateRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/applications/{applicationId}/stages")
public class ApplicationStageController {

    private final ApplicationStageService service;

    public ApplicationStageController(ApplicationStageService service) {
        this.service = service;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApplicationStageResponse create(
            @PathVariable Long applicationId,
            @Valid @RequestBody ApplicationStageCreateRequest request) {
        return service.create(applicationId, request);
    }

    @GetMapping
    public List<ApplicationStageResponse> findAll(@PathVariable Long applicationId) {
        return service.findAll(applicationId);
    }

    @GetMapping("/{id}")
    public ApplicationStageResponse findById(@PathVariable Long applicationId, @PathVariable Long id) {
        return service.findById(applicationId, id);
    }

    @PatchMapping("/{id}")
    public ApplicationStageResponse update(
            @PathVariable Long applicationId,
            @PathVariable Long id,
            @Valid @RequestBody ApplicationStageUpdateRequest request) {
        return service.update(applicationId, id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long applicationId, @PathVariable Long id) {
        service.delete(applicationId, id);
    }
}
