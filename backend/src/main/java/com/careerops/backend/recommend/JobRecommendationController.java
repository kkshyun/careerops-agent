package com.careerops.backend.recommend;

import com.careerops.backend.recommend.dto.JobRecommendationResponse;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.ConstraintViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@Validated
@RestController
@RequestMapping("/api/jobs")
public class JobRecommendationController {
    private final JobRecommendationService service;
    public JobRecommendationController(JobRecommendationService service){ this.service=service; }
    @PostMapping("/recommendations")
    public JobRecommendationResponse recommend(@RequestParam(defaultValue="5") @Min(1) @Max(20) int limit){ return service.recommend(limit); }
    @ExceptionHandler(JobRecommendationException.class) @ResponseStatus(HttpStatus.BAD_GATEWAY) public void recommendationFailure(){}
    @ExceptionHandler(ConstraintViolationException.class) @ResponseStatus(HttpStatus.BAD_REQUEST) public void invalidLimit(){}
}
