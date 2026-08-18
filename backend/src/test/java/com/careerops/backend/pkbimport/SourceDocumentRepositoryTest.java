package com.careerops.backend.pkbimport;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.data.domain.PageRequest;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class SourceDocumentRepositoryTest {
    @Autowired SourceDocumentRepository repository;
    @Autowired EntityManager entityManager;

    @Test
    void savesAndFindsDocumentIncludingFiftyThousandCharacterBoundary() {
        String rawText = "가".repeat(50_000);
        SourceDocument saved = repository.saveAndFlush(
                new SourceDocument(null, DocumentType.RESUME, "a".repeat(64), rawText));

        SourceDocument found = repository.findById(saved.getId()).orElseThrow();
        assertThat(found.getFileName()).isNull();
        assertThat(found.getDocumentType()).isEqualTo(DocumentType.RESUME);
        assertThat(found.getContentHash()).hasSize(64);
        assertThat(found.getRawText()).hasSize(50_000);
        assertThat(found.getCreatedAt()).isNotNull();
    }

    @Test
    void filtersByDocumentTypeAndOrdersByCreatedAtDescending() {
        SourceDocument older = repository.saveAndFlush(document("older", DocumentType.RESUME));
        SourceDocument newer = repository.saveAndFlush(document("newer", DocumentType.RESUME));
        repository.saveAndFlush(document("portfolio", DocumentType.PORTFOLIO));
        entityManager.createNativeQuery("UPDATE source_documents SET created_at = :createdAt WHERE id = :id")
                .setParameter("createdAt", Instant.parse("2025-01-01T00:00:00Z"))
                .setParameter("id", older.getId()).executeUpdate();
        entityManager.createNativeQuery("UPDATE source_documents SET created_at = :createdAt WHERE id = :id")
                .setParameter("createdAt", Instant.parse("2026-01-01T00:00:00Z"))
                .setParameter("id", newer.getId()).executeUpdate();
        entityManager.clear();

        assertThat(repository.search(DocumentType.RESUME, PageRequest.of(0, 10)).getContent())
                .extracting(SourceDocument::getFileName).containsExactly("newer", "older");
        assertThat(repository.search(null, PageRequest.of(0, 10)).getTotalElements()).isEqualTo(3);
    }

    private SourceDocument document(String fileName, DocumentType type) {
        return new SourceDocument(fileName, type, "b".repeat(64), fileName + " text");
    }
}
