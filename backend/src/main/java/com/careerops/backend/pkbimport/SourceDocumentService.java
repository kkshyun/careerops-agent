package com.careerops.backend.pkbimport;

import com.careerops.backend.pkbimport.dto.*;
import com.careerops.backend.pkbimport.extraction.DocumentExtractionException;
import com.careerops.backend.pkbimport.extraction.DocumentTextExtractionService;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
public class SourceDocumentService {
    private static final long MAX_FILE_SIZE = 10L * 1024 * 1024;
    private static final int MAX_RAW_TEXT_LENGTH = 50_000;
    private static final int MAX_FILE_NAME_LENGTH = 255;
    private static final Set<String> SUPPORTED_EXTENSIONS = Set.of("pdf", "docx");
    private static final Map<String, String> CONTENT_TYPES = Map.of(
            "pdf", MediaType.APPLICATION_PDF_VALUE,
            "docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document");
    private static final byte[] PDF_MAGIC = {'%', 'P', 'D', 'F', '-'};
    private static final byte[] DOCX_MAGIC = {'P', 'K', 3, 4};

    private final SourceDocumentRepository repository;
    private final DocumentTextExtractionService extractionService;

    public SourceDocumentService(SourceDocumentRepository repository,
                                 DocumentTextExtractionService extractionService) {
        this.repository = repository;
        this.extractionService = extractionService;
    }

    public SourceDocumentDetailResponse create(SourceDocumentCreateRequest request) {
        SourceDocument entity = repository.save(new SourceDocument(request.fileName(), request.documentType(),
                sha256(request.rawText()), request.rawText()));
        return SourceDocumentDetailResponse.from(entity);
    }

    public SourceDocumentDetailResponse createFromUpload(MultipartFile file, DocumentType documentType) {
        if (file.isEmpty()) {
            throw badRequest("업로드 파일이 비어 있습니다");
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "파일 크기는 10MB 이하여야 합니다");
        }

        String fileName = file.getOriginalFilename();
        String extension = extensionOf(fileName);
        if (!SUPPORTED_EXTENSIONS.contains(extension)) {
            throw badRequest("지원하지 않는 파일 형식입니다");
        }
        if (!CONTENT_TYPES.get(extension).equals(file.getContentType())) {
            throw badRequest("확장자와 Content-Type이 일치하지 않습니다");
        }
        if (!hasExpectedMagic(file, extension)) {
            throw badRequest("확장자와 실제 파일 내용이 일치하지 않습니다");
        }

        String rawText;
        try (InputStream inputStream = file.getInputStream()) {
            rawText = extractionService.extract(extension, inputStream);
        } catch (DocumentExtractionException | IOException exception) {
            throw badRequest("문서에서 텍스트를 추출할 수 없습니다");
        }
        if (rawText == null || rawText.isBlank()) {
            throw badRequest("추출 가능한 텍스트가 없습니다");
        }
        if (rawText.length() > MAX_RAW_TEXT_LENGTH) {
            throw badRequest("추출된 텍스트는 50000자 이하여야 합니다");
        }
        if (fileName != null && fileName.length() > MAX_FILE_NAME_LENGTH) {
            throw badRequest("파일명은 255자 이하여야 합니다");
        }
        return create(new SourceDocumentCreateRequest(fileName, documentType, rawText));
    }

    private String extensionOf(String fileName) {
        if (fileName == null) return "";
        int dot = fileName.lastIndexOf('.');
        if (dot < 0 || dot == fileName.length() - 1) return "";
        return fileName.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    private boolean hasExpectedMagic(MultipartFile file, String extension) {
        byte[] expected = extension.equals("pdf") ? PDF_MAGIC : DOCX_MAGIC;
        try (InputStream inputStream = file.getInputStream()) {
            return java.util.Arrays.equals(inputStream.readNBytes(expected.length), expected);
        } catch (IOException exception) {
            throw badRequest("파일을 읽을 수 없습니다");
        }
    }

    private ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
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
