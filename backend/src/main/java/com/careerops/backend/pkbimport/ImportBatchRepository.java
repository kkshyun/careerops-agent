package com.careerops.backend.pkbimport;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import java.util.Optional;

public interface ImportBatchRepository extends JpaRepository<ImportBatch, Long> {
    @Query("SELECT b FROM ImportBatch b " +
            "WHERE (:sourceDocumentId IS NULL OR b.sourceDocument.id = :sourceDocumentId) " +
            "ORDER BY b.createdAt DESC")
    Page<ImportBatch> search(@Param("sourceDocumentId") Long sourceDocumentId, Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT b FROM ImportBatch b WHERE b.id = :id")
    Optional<ImportBatch> findByIdForUpdate(@Param("id") Long id);
}
