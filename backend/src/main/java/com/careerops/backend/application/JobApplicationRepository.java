package com.careerops.backend.application;

import com.careerops.backend.application.dto.JobApplicationResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface JobApplicationRepository extends JpaRepository<JobApplication, Long> {

    @Query("""
            SELECT new com.careerops.backend.application.dto.JobApplicationResponse(
                a.id, a.status, a.memo, a.appliedAt, a.createdAt, a.updatedAt,
                p.id, p.companyName, p.title, p.applicationEndAt, p.status)
            FROM JobApplication a JOIN a.jobPosting p
            WHERE (:status IS NULL OR a.status = :status)
            ORDER BY a.updatedAt DESC
            """)
    Page<JobApplicationResponse> search(@Param("status") ApplicationStatus status, Pageable pageable);

    @Query("""
            SELECT new com.careerops.backend.application.dto.JobApplicationResponse(
                a.id, a.status, a.memo, a.appliedAt, a.createdAt, a.updatedAt,
                p.id, p.companyName, p.title, p.applicationEndAt, p.status)
            FROM JobApplication a JOIN a.jobPosting p
            WHERE a.id = :id
            """)
    Optional<JobApplicationResponse> findResponseById(@Param("id") Long id);

    boolean existsByJobPostingId(Long jobPostingId);
}
