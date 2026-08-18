package com.careerops.backend.pkbimport;

import com.careerops.backend.pkbimport.dto.ImportBatchListResponse;
import com.careerops.backend.pkbimport.dto.ImportBatchResponse;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ImportBatchService {
    private final ImportBatchRepository repository;
    private final SourceDocumentRepository sourceDocumentRepository;

    public ImportBatchService(ImportBatchRepository repository, SourceDocumentRepository sourceDocumentRepository) {
        this.repository = repository;
        this.sourceDocumentRepository = sourceDocumentRepository;
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
}
