package com.careerops.backend.pkbimport;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.data.domain.PageRequest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class ImportBatchRepositoryTest {
    @Autowired ImportBatchRepository repository;
    @Autowired SourceDocumentRepository sourceDocumentRepository;
    @Autowired EntityManager entityManager;

    @Test
    void savesFindsAndFiltersBySourceDocumentId() {
        SourceDocument first = sourceDocumentRepository.save(document("first"));
        SourceDocument second = sourceDocumentRepository.saveAndFlush(document("second"));
        ImportBatch older = repository.saveAndFlush(new ImportBatch(first, ImportBatchStatus.OPEN));
        ImportBatch newer = repository.saveAndFlush(new ImportBatch(first, ImportBatchStatus.OPEN));
        repository.saveAndFlush(new ImportBatch(second, ImportBatchStatus.OPEN));
        entityManager.createNativeQuery(
                        "UPDATE import_batches SET created_at = TIMESTAMP WITH TIME ZONE '2025-01-01 00:00:00+00' WHERE id = :id")
                .setParameter("id", older.getId()).executeUpdate();
        entityManager.createNativeQuery(
                        "UPDATE import_batches SET created_at = TIMESTAMP WITH TIME ZONE '2026-01-01 00:00:00+00' WHERE id = :id")
                .setParameter("id", newer.getId()).executeUpdate();
        entityManager.clear();

        assertThat(repository.search(first.getId(), PageRequest.of(0, 10)).getContent())
                .extracting(ImportBatch::getId).containsExactly(newer.getId(), older.getId());
        ImportBatch found = repository.findById(newer.getId()).orElseThrow();
        assertThat(found.getSourceDocument().getId()).isEqualTo(first.getId());
        assertThat(found.getStatus()).isEqualTo(ImportBatchStatus.OPEN);
        assertThat(found.getCompletedAt()).isNull();
        assertThat(found.getCreatedAt()).isNotNull();
    }

    @Test
    void databaseRejectsOrphanBatch() {
        assertThatThrownBy(() -> {
            entityManager.createNativeQuery("""
                    INSERT INTO import_batches (source_document_id, status, created_at)
                    VALUES (:sourceDocumentId, 'OPEN', CURRENT_TIMESTAMP)
                    """).setParameter("sourceDocumentId", Long.MAX_VALUE).executeUpdate();
            entityManager.flush();
        }).isInstanceOf(RuntimeException.class);
    }

    private SourceDocument document(String fileName) {
        return new SourceDocument(fileName, DocumentType.OTHER, "c".repeat(64), fileName + " text");
    }
}
