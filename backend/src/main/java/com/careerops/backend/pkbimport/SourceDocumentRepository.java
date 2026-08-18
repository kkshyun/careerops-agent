package com.careerops.backend.pkbimport;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SourceDocumentRepository extends JpaRepository<SourceDocument, Long> {
    @Query("SELECT d FROM SourceDocument d " +
            "WHERE (:documentType IS NULL OR d.documentType = :documentType) " +
            "ORDER BY d.createdAt DESC")
    Page<SourceDocument> search(@Param("documentType") DocumentType documentType, Pageable pageable);
}
