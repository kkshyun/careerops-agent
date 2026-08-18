package com.careerops.backend.career;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface CertificationRepository extends JpaRepository<Certification, Long> {
    @Query("SELECT c FROM Certification c ORDER BY c.acquiredDate DESC NULLS LAST")
    Page<Certification> findAllOrderByAcquiredDateDescNullsLast(Pageable pageable);
}
