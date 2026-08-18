package com.careerops.backend.pkbimport;

import com.careerops.backend.pkbimport.dto.*;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

@Service
public class SourceDocumentService {
    private final SourceDocumentRepository repository;

    public SourceDocumentService(SourceDocumentRepository repository) {
        this.repository = repository;
    }

    public SourceDocumentDetailResponse create(SourceDocumentCreateRequest request) {
        SourceDocument entity = repository.save(new SourceDocument(request.fileName(), request.documentType(),
                sha256(request.rawText()), request.rawText()));
        return SourceDocumentDetailResponse.from(entity);
    }

    public SourceDocumentListResponse findAll(DocumentType documentType, Pageable pageable) {
        int size = Math.min(pageable.getPageSize(), 100);
        return SourceDocumentListResponse.from(repository.search(documentType,
                PageRequest.of(pageable.getPageNumber(), size)));
    }

    public SourceDocumentDetailResponse findById(Long id) {
        return SourceDocumentDetailResponse.from(find(id));
    }

    private SourceDocument find(Long id) {
        return repository.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }

    private String sha256(String rawText) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(rawText.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }
}
