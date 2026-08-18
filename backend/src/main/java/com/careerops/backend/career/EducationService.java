package com.careerops.backend.career;

import com.careerops.backend.career.dto.*;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import java.math.BigDecimal;
import java.time.LocalDate;

@Service
public class EducationService {
    private final EducationRepository repository;

    public EducationService(EducationRepository repository) { this.repository = repository; }

    public EducationResponse create(EducationCreateRequest request) {
        validateDates(request.startDate(), request.endDate());
        validateGpa(request.gpa(), request.gpaScale());
        Education entity = repository.save(new Education(request.institution(), request.major(), request.degree(),
                request.status(), request.startDate(), request.endDate(), request.gpa(), request.gpaScale(),
                request.description()));
        return EducationResponse.from(entity);
    }

    public EducationListResponse findAll(Pageable pageable) {
        int size = Math.min(pageable.getPageSize(), 100);
        return EducationListResponse.from(repository.findAllOrderByStartDateDescNullsLast(
                PageRequest.of(pageable.getPageNumber(), size)));
    }

    public EducationResponse findById(Long id) { return EducationResponse.from(find(id)); }

    public EducationResponse update(Long id, EducationUpdateRequest request) {
        Education entity = find(id);
        LocalDate startDate = request.startDate() == null ? entity.getStartDate() : request.startDate();
        LocalDate endDate = request.endDate() == null ? entity.getEndDate() : request.endDate();
        BigDecimal gpa = request.gpa() == null ? entity.getGpa() : request.gpa();
        BigDecimal gpaScale = request.gpaScale() == null ? entity.getGpaScale() : request.gpaScale();
        validateDates(startDate, endDate);
        validateGpa(gpa, gpaScale);
        if (request.institution() != null) entity.updateInstitution(request.institution());
        if (request.major() != null) entity.updateMajor(request.major());
        if (request.degree() != null) entity.updateDegree(request.degree());
        if (request.status() != null) entity.updateStatus(request.status());
        if (request.startDate() != null) entity.updateStartDate(request.startDate());
        if (request.endDate() != null) entity.updateEndDate(request.endDate());
        if (request.gpa() != null) entity.updateGpa(request.gpa());
        if (request.gpaScale() != null) entity.updateGpaScale(request.gpaScale());
        if (request.description() != null) entity.updateDescription(request.description());
        return EducationResponse.from(repository.saveAndFlush(entity));
    }

    public void delete(Long id) {
        Education entity = find(id);
        repository.delete(entity);
        repository.flush();
    }

    private Education find(Long id) {
        return repository.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }

    private void validateDates(LocalDate startDate, LocalDate endDate) {
        if (startDate != null && endDate != null && endDate.isBefore(startDate)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "endDate must not be before startDate");
        }
    }

    private void validateGpa(BigDecimal gpa, BigDecimal gpaScale) {
        if ((gpa == null) != (gpaScale == null)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "gpa and gpaScale must be provided together or both omitted");
        }
        if (gpa == null) return;
        if (gpa.compareTo(BigDecimal.ZERO) < 0 || gpaScale.compareTo(BigDecimal.ZERO) <= 0
                || gpa.compareTo(gpaScale) > 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "gpa must be non-negative, gpaScale must be positive, and gpa must not exceed gpaScale");
        }
    }
}
