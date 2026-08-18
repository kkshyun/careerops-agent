package com.careerops.backend.pkbimport;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.annotation.Rollback;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Transactional
@Rollback
class ImportCandidateRepositoryTest {
    @Autowired SourceDocumentRepository documentRepository;
    @Autowired ImportBatchRepository batchRepository;
    @Autowired ImportCandidateRepository repository;

    @Test
    void savesSearchesAndTransitionsOnlyPendingCandidate() {
        SourceDocument document = documentRepository.save(new SourceDocument("repo", DocumentType.OTHER,
                "d".repeat(64), "raw"));
        ImportBatch batch = batchRepository.save(new ImportBatch(document, ImportBatchStatus.OPEN));
        ImportCandidate candidate = repository.saveAndFlush(new ImportCandidate(batch, CandidateTargetType.AWARD,
                "{\"title\":\"repo\"}"));
        assertThat(repository.search(batch.getId(), ImportCandidateStatus.PENDING,
                PageRequest.of(0, 20)).getTotalElements()).isEqualTo(1L);
        assertThat(repository.transitionIfPending(candidate.getId(), batch.getId(),
                ImportCandidateStatus.REJECTED, Instant.now())).isEqualTo(1);
        assertThat(repository.transitionIfPending(candidate.getId(), batch.getId(),
                ImportCandidateStatus.APPROVED, Instant.now())).isZero();
        assertThat(repository.findByIdAndImportBatchId(candidate.getId(), batch.getId())).isPresent();
    }
}
