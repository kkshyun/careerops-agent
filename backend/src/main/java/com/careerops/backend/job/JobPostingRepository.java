package com.careerops.backend.job;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Optional;

public interface JobPostingRepository extends JpaRepository<JobPosting, Long> {
    Optional<JobPosting> findFirstBySourceAndExternalId(String source, String externalId);

    Optional<JobPosting> findFirstBySourceAndSourceUrl(String source, String sourceUrl);

    @Query("""
            SELECT j FROM JobPosting j
            WHERE (:status IS NULL OR j.status = :status)
              AND (:careerLevel IS NULL OR j.careerLevel LIKE :careerLevel)
              AND (:companyName IS NULL OR j.companyName LIKE :companyName)
              AND (:jobCategory IS NULL OR j.jobCategory LIKE :jobCategory)
            ORDER BY j.applicationEndAt ASC NULLS LAST
            """)
    Page<JobPosting> search(
            @Param("status") String status,
            @Param("careerLevel") String careerLevel,
            @Param("companyName") String companyName,
            @Param("jobCategory") String jobCategory,
            Pageable pageable);
}
