package com.careerops.backend.pkbimport;

import com.careerops.backend.pkbimport.dto.ImportBatchListResponse;
import com.careerops.backend.pkbimport.dto.ImportBatchResponse;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ImportBatchService {
    private final ImportBatchRepository repository;
    private final SourceDocumentRepository sourceDocumentRepository;
    private final ImportCandidateRepository candidateRepository;

    public ImportBatchService(ImportBatchRepository repository, SourceDocumentRepository sourceDocumentRepository,
                              ImportCandidateRepository candidateRepository) {
        this.repository = repository;
        this.sourceDocumentRepository = sourceDocumentRepository;
        this.candidateRepository = candidateRepository;
    }

    public ImportBatchResponse create(Long sourceDocumentId) {
        SourceDocument sourceDocument = sourceDocumentRepository.findById(sourceDocumentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        return ImportBatchResponse.from(repository.save(new ImportBatch(sourceDocument, ImportBatchStatus.OPEN)));
    }

    public ImportBatchListResponse findAll(Long sourceDocumentId, Pageable pageable) {
        int size = Math.min(pageable.getPageSize(), 100);
        return ImportBatchListResponse.from(repository.search(sourceDocumentId,
                PageRequest.of(pageable.getPageNumber(), size)));
    }

    public ImportBatchResponse findById(Long id) {
        ImportBatch entity = repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        return ImportBatchResponse.from(entity);
    }

    @Transactional
    public ImportBatchResponse complete(Long id) {
        ImportBatch batch = repository.findByIdForUpdate(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        if (batch.getStatus() != ImportBatchStatus.OPEN) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "import batch already completed");
        }
        if (candidateRepository.existsByImportBatchIdAndStatus(id, ImportCandidateStatus.PENDING)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "import batch has pending candidates");
        }
        batch.markCompleted();
        return ImportBatchResponse.from(repository.save(batch));
    }
}
