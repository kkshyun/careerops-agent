package com.careerops.backend.pkbimport;

import com.careerops.backend.career.CareerExperience;
import com.careerops.backend.career.CareerExperienceRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class ImportCandidateApproveRollbackNonTransactionalTest {
    @Autowired MockMvc mockMvc;
    @Autowired SourceDocumentRepository documentRepository;
    @Autowired ImportBatchRepository batchRepository;
    @Autowired ImportCandidateRepository candidateRepository;
    @Autowired CareerExperienceRepository experienceRepository;

    private Long documentId;
    private Long batchId;
    private Long candidateId;

    @BeforeEach
    void setUp() {
        SourceDocument document = documentRepository.save(new SourceDocument("rollback", DocumentType.OTHER,
                "b".repeat(64), "raw"));
        ImportBatch batch = batchRepository.save(new ImportBatch(document, ImportBatchStatus.OPEN));
        ImportCandidate candidate = candidateRepository.save(new ImportCandidate(batch,
                CandidateTargetType.CAREER_EXPERIENCE,
                "{\"type\":\"PROJECT\",\"title\":\"x\",\"startDate\":\"2026-02-01\",\"endDate\":\"2026-01-01\"}"));
        documentId = document.getId();
        batchId = batch.getId();
        candidateId = candidate.getId();
    }

    @AfterEach
    void cleanUp() {
        experienceRepository.deleteAll(experienceRepository.findAll().stream()
                .filter(entity -> candidateId.equals(entity.getSourceImportCandidateId())).toList());
        experienceRepository.flush();
        candidateRepository.deleteById(candidateId);
        candidateRepository.flush();
        batchRepository.deleteById(batchId);
        batchRepository.flush();
        documentRepository.deleteById(documentId);
        documentRepository.flush();
    }

    @Test
    void approvalBusinessFailureRollsBackTransitionAndPkbInsert() throws Exception {
        long before = experienceRepository.count();
        mockMvc.perform(post("/api/career/imports/batches/{batchId}/candidates/{candidateId}/approve",
                        batchId, candidateId))
                .andExpect(status().isBadRequest());

        assertThat(candidateRepository.findById(candidateId).orElseThrow().getStatus())
                .isEqualTo(ImportCandidateStatus.PENDING);
        assertThat(experienceRepository.count()).isEqualTo(before);
    }
}
