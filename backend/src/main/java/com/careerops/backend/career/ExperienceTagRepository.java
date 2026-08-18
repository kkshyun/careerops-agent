package com.careerops.backend.career;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface ExperienceTagRepository extends JpaRepository<ExperienceTag, Long> {
    List<ExperienceTag> findByCareerExperienceIdOrderByIdAsc(Long careerExperienceId);
    void deleteByCareerExperienceId(Long careerExperienceId);
    long countByCareerExperienceId(Long careerExperienceId);
}
