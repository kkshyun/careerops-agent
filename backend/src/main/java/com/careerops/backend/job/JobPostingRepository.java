package com.careerops.backend.job;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface JobPostingRepository extends JpaRepository<JobPosting, Long> {
    Optional<JobPosting> findFirstBySourceAndExternalId(String source, String externalId);

    Optional<JobPosting> findFirstBySourceAndSourceUrl(String source, String sourceUrl);
}
