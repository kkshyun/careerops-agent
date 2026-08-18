package com.careerops.backend.application;

import com.careerops.backend.application.dto.ApplicationStageCreateRequest;
import com.careerops.backend.application.dto.ApplicationStageResponse;
import com.careerops.backend.application.dto.ApplicationStageUpdateRequest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
public class ApplicationStageService {

    private final ApplicationStageRepository repository;
    private final JobApplicationRepository jobApplicationRepository;

    public ApplicationStageService(
            ApplicationStageRepository repository,
            JobApplicationRepository jobApplicationRepository) {
        this.repository = repository;
        this.jobApplicationRepository = jobApplicationRepository;
    }

    public ApplicationStageResponse create(Long applicationId, ApplicationStageCreateRequest request) {
        JobApplication application = findApplication(applicationId);
        Integer sortOrder = request.sortOrder() == null
                ? repository.findTopByJobApplicationIdOrderBySortOrderDesc(applicationId)
                        .map(stage -> stage.getSortOrder() + 1)
                        .orElse(1)
                : request.sortOrder();
        StageResult result = request.result() == null ? StageResult.PENDING : request.result();

        try {
            return ApplicationStageResponse.from(repository.saveAndFlush(new ApplicationStage(
                    application, request.stageType(), request.label(), sortOrder,
                    request.scheduledAt(), result, request.memo())));
        } catch (DataIntegrityViolationException exception) {
            throw conflict(exception);
        }
    }

    public List<ApplicationStageResponse> findAll(Long applicationId) {
        findApplication(applicationId);
        return repository.findByJobApplicationIdOrderBySortOrderAsc(applicationId).stream()
                .map(ApplicationStageResponse::from)
                .toList();
    }

    public ApplicationStageResponse findById(Long applicationId, Long id) {
        findApplication(applicationId);
        return ApplicationStageResponse.from(findStage(applicationId, id));
    }

    public ApplicationStageResponse update(
            Long applicationId, Long id, ApplicationStageUpdateRequest request) {
        findApplication(applicationId);
        ApplicationStage stage = findStage(applicationId, id);
        if (request.label() != null) stage.updateLabel(request.label());
        if (request.sortOrder() != null) stage.updateSortOrder(request.sortOrder());
        if (request.scheduledAt() != null) stage.updateScheduledAt(request.scheduledAt());
        if (request.result() != null) stage.updateResult(request.result());
        if (request.memo() != null) stage.updateMemo(request.memo());

        try {
            return ApplicationStageResponse.from(repository.saveAndFlush(stage));
        } catch (DataIntegrityViolationException exception) {
            throw conflict(exception);
        }
    }

    public void delete(Long applicationId, Long id) {
        findApplication(applicationId);
        repository.delete(findStage(applicationId, id));
    }

    private JobApplication findApplication(Long applicationId) {
        return jobApplicationRepository.findById(applicationId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }

    private ApplicationStage findStage(Long applicationId, Long id) {
        return repository.findByIdAndJobApplicationId(id, applicationId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }

    private ResponseStatusException conflict(DataIntegrityViolationException exception) {
        return new ResponseStatusException(HttpStatus.CONFLICT, "Stage sort order already exists", exception);
    }
}
