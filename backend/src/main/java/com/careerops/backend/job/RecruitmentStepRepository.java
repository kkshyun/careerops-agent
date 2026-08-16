package com.careerops.backend.job;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface RecruitmentStepRepository extends JpaRepository<RecruitmentStep, Long> {
    boolean existsByRecrutStepSn(Long recrutStepSn);
    List<RecruitmentStep> findByJobPostingId(Long jobPostingId);
}
