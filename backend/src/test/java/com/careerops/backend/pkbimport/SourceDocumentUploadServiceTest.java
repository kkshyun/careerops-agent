package com.careerops.backend.pkbimport;

import com.careerops.backend.pkbimport.extraction.DocumentTextExtractionService;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class SourceDocumentUploadServiceTest {
    private static final String PDF_TYPE = "application/pdf";

    @Test
    void acceptsExactlyFiftyThousandExtractedCharacters() {
        SourceDocumentRepository repository = mock(SourceDocumentRepository.class);
        DocumentTextExtractionService extraction = mock(DocumentTextExtractionService.class);
        when(extraction.extract(eq("pdf"), any())).thenReturn("x".repeat(50_000));
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        SourceDocumentService service = new SourceDocumentService(repository, extraction);

        assertThat(service.createFromUpload(pdf("boundary.pdf", "%PDF-ok".getBytes()), DocumentType.OTHER).rawText())
                .hasSize(50_000);
        verify(repository).save(any());
    }

    @Test
    void rejectsTextOverBoundaryAndFileNameOverBoundaryBeforeSaving() {
        SourceDocumentRepository repository = mock(SourceDocumentRepository.class);
        DocumentTextExtractionService extraction = mock(DocumentTextExtractionService.class);
        SourceDocumentService service = new SourceDocumentService(repository, extraction);
        when(extraction.extract(eq("pdf"), any())).thenReturn("x".repeat(50_001));
        assertBadRequest(() -> service.createFromUpload(pdf("large.pdf", "%PDF-ok".getBytes()), DocumentType.RESUME));

        when(extraction.extract(eq("pdf"), any())).thenReturn("text");
        String tooLongName = "a".repeat(252) + ".pdf";
        assertBadRequest(() -> service.createFromUpload(pdf(tooLongName, "%PDF-ok".getBytes()), DocumentType.RESUME));
        verify(repository, never()).save(any());
    }

    @Test
    void rejectsApplicationLevelFileLargerThanTenMegabytesWith413() {
        SourceDocumentService service = new SourceDocumentService(mock(SourceDocumentRepository.class),
                mock(DocumentTextExtractionService.class));
        byte[] bytes = new byte[10 * 1024 * 1024 + 1];
        System.arraycopy("%PDF-".getBytes(StandardCharsets.US_ASCII), 0, bytes, 0, 5);
        assertThatThrownBy(() -> service.createFromUpload(pdf("large.pdf", bytes), DocumentType.RESUME))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        error -> assertThat(error.getStatusCode()).isEqualTo(HttpStatus.PAYLOAD_TOO_LARGE));
    }

    private MockMultipartFile pdf(String name, byte[] bytes) {
        return new MockMultipartFile("file", name, PDF_TYPE, bytes);
    }

    private void assertBadRequest(org.assertj.core.api.ThrowableAssert.ThrowingCallable action) {
        assertThatThrownBy(action).isInstanceOfSatisfying(ResponseStatusException.class,
                error -> assertThat(error.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
    }
}
