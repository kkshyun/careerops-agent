package com.careerops.backend.career;

import com.careerops.backend.career.dto.CareerExperienceResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CareerExperienceRepository extends JpaRepository<CareerExperience, Long> {
    @Query("""
        SELECT new com.careerops.backend.career.dto.CareerExperienceResponse(
          e.id,e.type,e.title,e.organization,e.role,e.startDate,e.endDate,e.summary,e.createdAt,e.updatedAt)
        FROM CareerExperience e
        WHERE (:type IS NULL OR e.type = :type)
          AND (:keyword IS NULL OR LOWER(e.title) LIKE LOWER(CONCAT('%', :keyword, '%')))
        ORDER BY e.updatedAt DESC
        """)
    Page<CareerExperienceResponse> search(@Param("type") ExperienceType type,
                                          @Param("keyword") String keyword, Pageable pageable);
    Page<CareerExperience> findByType(ExperienceType type, Pageable pageable);
}
