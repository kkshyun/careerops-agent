package com.careerops.backend.career;

import com.careerops.backend.career.dto.*;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AwardService {
    private final AwardRepository repository;

    public AwardService(AwardRepository repository) { this.repository = repository; }

    public AwardResponse create(AwardCreateRequest request) {
        return create(request, SourceType.MANUAL, null);
    }

    public AwardResponse create(AwardCreateRequest request, SourceType sourceType, Long sourceImportCandidateId) {
        Award entity = repository.save(new Award(request.title(), request.issuer(),
                request.awardedDate(), request.description(), sourceType, sourceImportCandidateId));
        return AwardResponse.from(entity);
    }

    public AwardListResponse findAll(Pageable pageable) {
        int size = Math.min(pageable.getPageSize(), 100);
        return AwardListResponse.from(repository.findAllOrderByAwardedDateDescNullsLast(
                PageRequest.of(pageable.getPageNumber(), size)));
    }

    public AwardResponse findById(Long id) { return AwardResponse.from(find(id)); }

    public AwardResponse update(Long id, AwardUpdateRequest request) {
        Award entity = find(id);
        if (request.title() != null) entity.updateTitle(request.title());
        if (request.issuer() != null) entity.updateIssuer(request.issuer());
        if (request.awardedDate() != null) entity.updateAwardedDate(request.awardedDate());
        if (request.description() != null) entity.updateDescription(request.description());
        return AwardResponse.from(repository.saveAndFlush(entity));
    }

    public void delete(Long id) {
        Award entity = find(id);
        repository.delete(entity);
        repository.flush();
    }

    private Award find(Long id) {
        return repository.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }
}
