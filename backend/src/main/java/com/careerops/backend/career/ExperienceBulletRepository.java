package com.careerops.backend.career;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface ExperienceBulletRepository extends JpaRepository<ExperienceBullet, Long> {
    List<ExperienceBullet> findByCareerExperienceIdOrderBySortOrderAsc(Long careerExperienceId);
    void deleteByCareerExperienceId(Long careerExperienceId);
    long countByCareerExperienceId(Long careerExperienceId);
}
