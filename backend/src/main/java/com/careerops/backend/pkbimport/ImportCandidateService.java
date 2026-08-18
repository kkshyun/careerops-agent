package com.careerops.backend.pkbimport;

import com.careerops.backend.career.*;
import com.careerops.backend.career.dto.*;
import com.careerops.backend.pkbimport.dto.*;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import org.springframework.data.domain.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class ImportCandidateService {
    private final ImportCandidateRepository repository;
    private final ImportBatchRepository batchRepository;
    private final ObjectMapper objectMapper;
    private final Validator validator;
    private final CareerExperienceService careerExperienceService;
    private final CertificationService certificationService;
    private final EducationService educationService;
    private final AwardService awardService;

    public ImportCandidateService(ImportCandidateRepository repository, ImportBatchRepository batchRepository,
                                  ObjectMapper objectMapper, Validator validator,
                                  CareerExperienceService careerExperienceService,
                                  CertificationService certificationService, EducationService educationService,
                                  AwardService awardService) {
        this.repository = repository; this.batchRepository = batchRepository; this.objectMapper = objectMapper;
        this.validator = validator; this.careerExperienceService = careerExperienceService;
        this.certificationService = certificationService; this.educationService = educationService;
        this.awardService = awardService;
    }

    @Transactional
    public ImportCandidateResponse create(Long batchId, ImportCandidateCreateRequest request) {
        ImportBatch batch = batchRepository.findByIdForUpdate(batchId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        if (batch.getStatus() != ImportBatchStatus.OPEN) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "import batch already completed");
        }
        parseAndValidate(request.targetType(), request.payload());
        return ImportCandidateResponse.from(repository.save(new ImportCandidate(batch, request.targetType(), request.payload())));
    }

    public ImportCandidateListResponse findAll(Long batchId, ImportCandidateStatus status, Pageable pageable) {
        if (!batchRepository.existsById(batchId)) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        int size = Math.min(pageable.getPageSize(), 100);
        return ImportCandidateListResponse.from(repository.search(batchId, status,
                PageRequest.of(pageable.getPageNumber(), size)));
    }

    public ImportCandidateResponse findById(Long batchId, Long candidateId) {
        return ImportCandidateResponse.from(find(batchId, candidateId));
    }

    @Transactional
    public ImportCandidateResponse approve(Long batchId, Long candidateId) {
        transitionFirst(batchId, candidateId, ImportCandidateStatus.APPROVED);
        ImportCandidate candidate = find(batchId, candidateId);
        Object request = parseAndValidate(candidate.getTargetType(), candidate.getPayload());
        Long entityId = switch (candidate.getTargetType()) {
            case CAREER_EXPERIENCE -> careerExperienceService.create((CareerExperienceCreateRequest) request,
                    SourceType.IMPORT, candidateId).id();
            case CERTIFICATION -> certificationService.create((CertificationCreateRequest) request,
                    SourceType.IMPORT, candidateId).id();
            case EDUCATION -> educationService.create((EducationCreateRequest) request,
                    SourceType.IMPORT, candidateId).id();
            case AWARD -> awardService.create((AwardCreateRequest) request, SourceType.IMPORT, candidateId).id();
        };
        candidate.setCreatedEntityId(entityId);
        return ImportCandidateResponse.from(repository.saveAndFlush(candidate));
    }

    @Transactional
    public ImportCandidateResponse reject(Long batchId, Long candidateId) {
        transitionFirst(batchId, candidateId, ImportCandidateStatus.REJECTED);
        return ImportCandidateResponse.from(find(batchId, candidateId));
    }

    private void transitionFirst(Long batchId, Long candidateId, ImportCandidateStatus target) {
        if (repository.transitionIfPending(candidateId, batchId, target, Instant.now()) == 0) {
            find(batchId, candidateId);
            throw new ResponseStatusException(HttpStatus.CONFLICT, "import candidate already reviewed");
        }
    }

    private ImportCandidate find(Long batchId, Long candidateId) {
        return repository.findByIdAndImportBatchId(candidateId, batchId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }

    private Object parseAndValidate(CandidateTargetType targetType, String payload) {
        Class<?> type = switch (targetType) {
            case CAREER_EXPERIENCE -> CareerExperienceCreateRequest.class;
            case CERTIFICATION -> CertificationCreateRequest.class;
            case EDUCATION -> EducationCreateRequest.class;
            case AWARD -> AwardCreateRequest.class;
        };
        final Object request;
        try {
            request = objectMapper.readValue(payload, type);
        } catch (Exception exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "invalid payload for targetType " + targetType);
        }
        Set<ConstraintViolation<Object>> violations = validator.validate(request);
        if (!violations.isEmpty()) {
            String message = violations.stream()
                    .map(v -> v.getPropertyPath() + " " + v.getMessage()).sorted().collect(Collectors.joining(", "));
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
        }
        return request;
    }
}
