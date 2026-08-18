package com.careerops.backend.pkbimport;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.time.Instant;
import java.util.Optional;

public interface ImportCandidateRepository extends JpaRepository<ImportCandidate, Long> {
    @Query("SELECT c FROM ImportCandidate c WHERE c.importBatch.id = :batchId " +
            "AND (:status IS NULL OR c.status = :status) ORDER BY c.createdAt DESC")
    Page<ImportCandidate> search(@Param("batchId") Long batchId,
                                 @Param("status") ImportCandidateStatus status, Pageable pageable);
    Optional<ImportCandidate> findByIdAndImportBatchId(Long id, Long batchId);
    boolean existsByImportBatchIdAndStatus(Long batchId, ImportCandidateStatus status);

    @Modifying(clearAutomatically = true)
    @Query("UPDATE ImportCandidate c SET c.status = :newStatus, c.reviewedAt = :reviewedAt " +
            "WHERE c.id = :id AND c.importBatch.id = :batchId AND c.status = 'PENDING'")
    int transitionIfPending(@Param("id") Long id, @Param("batchId") Long batchId,
                            @Param("newStatus") ImportCandidateStatus newStatus,
                            @Param("reviewedAt") Instant reviewedAt);
}
