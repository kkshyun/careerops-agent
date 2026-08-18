package com.careerops.backend.pkbimport;

import com.careerops.backend.career.dto.AwardCreateRequest;
import com.careerops.backend.pkbimport.extraction.llm.DocumentExtractionClient;
import com.careerops.backend.pkbimport.extraction.llm.LlmExtractionException;
import com.careerops.backend.pkbimport.extraction.llm.StructuredExtractionResult;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.annotation.Rollback;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@Rollback
class ImportBatchControllerTest {
    private static final String IMPORTS_URL = "/api/career/imports";

    @Autowired MockMvc mockMvc;
    @Autowired SourceDocumentRepository sourceDocumentRepository;
    @Autowired ImportBatchRepository repository;
    @Autowired ImportCandidateRepository candidateRepository;
    @MockitoBean DocumentExtractionClient extractionClient;

    @Test
    void createsOpenBatchForExistingDocument() throws Exception {
        SourceDocument document = sourceDocumentRepository.saveAndFlush(document("source"));

        mockMvc.perform(post(IMPORTS_URL + "/documents/{documentId}/batches", document.getId()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.sourceDocumentId").value(document.getId()))
                .andExpect(jsonPath("$.status").value("OPEN"))
                .andExpect(jsonPath("$.createdAt").isNotEmpty())
                .andExpect(jsonPath("$.completedAt").isEmpty());
    }

    @Test
    void missingDocumentReturnsNotFoundWithoutCreatingBatch() throws Exception {
        long before = repository.count();
        mockMvc.perform(post(IMPORTS_URL + "/documents/{documentId}/batches", Long.MAX_VALUE))
                .andExpect(status().isNotFound());
        assertThat(repository.count()).isEqualTo(before);
    }

    @Test
    void listsWithPaginationClampAndSourceDocumentFilter() throws Exception {
        SourceDocument first = sourceDocumentRepository.save(document("first"));
        SourceDocument second = sourceDocumentRepository.saveAndFlush(document("second"));
        repository.save(new ImportBatch(first, ImportBatchStatus.OPEN));
        repository.save(new ImportBatch(first, ImportBatchStatus.OPEN));
        repository.saveAndFlush(new ImportBatch(second, ImportBatchStatus.OPEN));

        mockMvc.perform(get(IMPORTS_URL + "/batches").param("sourceDocumentId", first.getId().toString())
                        .param("page", "1").param("size", "1"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.page").value(1)).andExpect(jsonPath("$.size").value(1))
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].sourceDocumentId").value(first.getId()));
        mockMvc.perform(get(IMPORTS_URL + "/batches").param("size", "1000"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.size").value(100));
    }

    @Test
    void getsExistingBatchAndReturnsNotFoundForMissingOne() throws Exception {
        SourceDocument document = sourceDocumentRepository.saveAndFlush(document("source"));
        ImportBatch batch = repository.saveAndFlush(new ImportBatch(document, ImportBatchStatus.OPEN));

        mockMvc.perform(get(IMPORTS_URL + "/batches/{id}", batch.getId()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.id").value(batch.getId()))
                .andExpect(jsonPath("$.sourceDocumentId").value(document.getId()));
        mockMvc.perform(get(IMPORTS_URL + "/batches/{id}", Long.MAX_VALUE)).andExpect(status().isNotFound());
    }

    @Test
    void completesOnlyOpenBatchWithoutPendingCandidates() throws Exception {
        ImportBatch batch = repository.saveAndFlush(new ImportBatch(
                sourceDocumentRepository.save(document("complete")), ImportBatchStatus.OPEN));
        mockMvc.perform(post(IMPORTS_URL + "/batches/{id}/complete", batch.getId()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.completedAt").isNotEmpty());
        mockMvc.perform(post(IMPORTS_URL + "/batches/{id}/complete", batch.getId()))
                .andExpect(status().isConflict());
        mockMvc.perform(post(IMPORTS_URL + "/batches/{id}/complete", Long.MAX_VALUE))
                .andExpect(status().isNotFound());
    }

    @Test
    void pendingCandidatePreventsCompletion() throws Exception {
        ImportBatch batch = repository.saveAndFlush(new ImportBatch(
                sourceDocumentRepository.save(document("pending")), ImportBatchStatus.OPEN));
        candidateRepository.saveAndFlush(new ImportCandidate(batch, CandidateTargetType.AWARD,
                "{\"title\":\"pending\"}"));
        mockMvc.perform(post(IMPORTS_URL + "/batches/{id}/complete", batch.getId()))
                .andExpect(status().isConflict());
        assertThat(repository.findById(batch.getId()).orElseThrow().getStatus()).isEqualTo(ImportBatchStatus.OPEN);
    }

    @Test
    void extractionEndpointReturnsStructuredSuccessResponse() throws Exception {
        ImportBatch batch = repository.saveAndFlush(new ImportBatch(
                sourceDocumentRepository.save(document("extract")), ImportBatchStatus.OPEN));
        when(extractionClient.extract(any(), any())).thenReturn(new StructuredExtractionResult(
                java.util.List.of(), java.util.List.of(), java.util.List.of(),
                java.util.List.of(new AwardCreateRequest("award", null, null, null))));

        mockMvc.perform(post(IMPORTS_URL + "/batches/{id}/extract", batch.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.batchId").value(batch.getId()))
                .andExpect(jsonPath("$.createdCandidateCount").value(1))
                .andExpect(jsonPath("$.counts.AWARD").value(1))
                .andExpect(jsonPath("$.counts.CAREER_EXPERIENCE").value(0))
                .andExpect(jsonPath("$.candidateIds.length()").value(1));
    }

    @Test
    void extractionEndpointMapsNotFoundConflictBadRequestAndProviderFailure() throws Exception {
        mockMvc.perform(post(IMPORTS_URL + "/batches/{id}/extract", Long.MAX_VALUE))
                .andExpect(status().isNotFound());

        ImportBatch malformed = repository.saveAndFlush(new ImportBatch(
                sourceDocumentRepository.save(document("malformed")), ImportBatchStatus.OPEN));
        doThrow(new LlmExtractionException(LlmExtractionException.Reason.MALFORMED_RESPONSE))
                .when(extractionClient).extract(any(), any());
        mockMvc.perform(post(IMPORTS_URL + "/batches/{id}/extract", malformed.getId()))
                .andExpect(status().isBadRequest());

        ImportBatch unavailable = repository.saveAndFlush(new ImportBatch(
                sourceDocumentRepository.save(document("unavailable")), ImportBatchStatus.OPEN));
        doThrow(new LlmExtractionException(LlmExtractionException.Reason.PROVIDER_RETRY_EXHAUSTED))
                .when(extractionClient).extract(any(), any());
        mockMvc.perform(post(IMPORTS_URL + "/batches/{id}/extract", unavailable.getId()))
                .andExpect(status().isServiceUnavailable());

        ImportBatch completed = repository.saveAndFlush(new ImportBatch(
                sourceDocumentRepository.save(document("closed")), ImportBatchStatus.OPEN));
        mockMvc.perform(post(IMPORTS_URL + "/batches/{id}/complete", completed.getId()))
                .andExpect(status().isOk());
        mockMvc.perform(post(IMPORTS_URL + "/batches/{id}/extract", completed.getId()))
                .andExpect(status().isConflict());
    }

    private SourceDocument document(String fileName) {
        return new SourceDocument(fileName, DocumentType.OTHER, "e".repeat(64), fileName + " raw text");
    }
}
