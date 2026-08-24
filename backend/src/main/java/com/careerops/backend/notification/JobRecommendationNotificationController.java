package com.careerops.backend.notification;

import com.careerops.backend.notification.dto.*;
import com.careerops.backend.recommend.JobRecommendationException;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.*;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@Validated
@RestController
@RequestMapping("/api/notifications/job-recommendations")
public class JobRecommendationNotificationController {
    private final NotificationPreparationService service;

    public JobRecommendationNotificationController(NotificationPreparationService service) { this.service = service; }

    @PostMapping
    public NotificationPreparationResponse prepare(
            @RequestParam(defaultValue = "5") @Min(1) @Max(20) int limit) {
        return service.prepare(limit);
    }

    @GetMapping
    public JobRecommendationNotificationListResponse search(
            @RequestParam(required = false) NotificationStatus status,
            @PageableDefault(size = 20) Pageable pageable) {
        return service.search(status, pageable);
    }

    @ExceptionHandler(JobRecommendationException.class)
    @ResponseStatus(HttpStatus.BAD_GATEWAY)
    public void recommendationFailure() {}

    @ExceptionHandler(ConstraintViolationException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public void invalidLimit() {}

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Void> responseStatus(ResponseStatusException exception) {
        return ResponseEntity.status(exception.getStatusCode()).build();
    }
}
