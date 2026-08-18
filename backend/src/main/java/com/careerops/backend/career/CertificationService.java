package com.careerops.backend.career;

import com.careerops.backend.career.dto.*;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import java.time.LocalDate;

@Service
public class CertificationService {
    private final CertificationRepository repository;

    public CertificationService(CertificationRepository repository) { this.repository = repository; }

    public CertificationResponse create(CertificationCreateRequest request) {
        validateDates(request.acquiredDate(), request.expirationDate());
        Certification entity = repository.save(new Certification(request.name(), request.issuer(),
                request.acquiredDate(), request.expirationDate(), request.credentialId(), request.description()));
        return CertificationResponse.from(entity);
    }

    public CertificationListResponse findAll(Pageable pageable) {
        int size = Math.min(pageable.getPageSize(), 100);
        return CertificationListResponse.from(repository.findAllOrderByAcquiredDateDescNullsLast(
                PageRequest.of(pageable.getPageNumber(), size)));
    }

    public CertificationResponse findById(Long id) { return CertificationResponse.from(find(id)); }

    public CertificationResponse update(Long id, CertificationUpdateRequest request) {
        Certification entity = find(id);
        LocalDate acquiredDate = request.acquiredDate() == null ? entity.getAcquiredDate() : request.acquiredDate();
        LocalDate expirationDate = request.expirationDate() == null ? entity.getExpirationDate() : request.expirationDate();
        validateDates(acquiredDate, expirationDate);
        if (request.name() != null) entity.updateName(request.name());
        if (request.issuer() != null) entity.updateIssuer(request.issuer());
        if (request.acquiredDate() != null) entity.updateAcquiredDate(request.acquiredDate());
        if (request.expirationDate() != null) entity.updateExpirationDate(request.expirationDate());
        if (request.credentialId() != null) entity.updateCredentialId(request.credentialId());
        if (request.description() != null) entity.updateDescription(request.description());
        return CertificationResponse.from(repository.saveAndFlush(entity));
    }

    public void delete(Long id) {
        Certification entity = find(id);
        repository.delete(entity);
        repository.flush();
    }

    private Certification find(Long id) {
        return repository.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }

    private void validateDates(LocalDate acquiredDate, LocalDate expirationDate) {
        if (acquiredDate != null && expirationDate != null && expirationDate.isBefore(acquiredDate)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "expirationDate must not be before acquiredDate");
        }
    }
}
