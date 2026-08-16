package com.careerops.backend.job;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface AttachmentRepository extends JpaRepository<Attachment, Long> {
    boolean existsByRecrutAtchFileNo(Long recrutAtchFileNo);
    List<Attachment> findByJobPostingId(Long jobPostingId);
    List<Attachment> findByJobPostingIdOrderBySortNoAscRecrutAtchFileNoAsc(Long jobPostingId);
}
