package com.careerops.backend.career;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface EducationRepository extends JpaRepository<Education, Long> {
    @Query("SELECT e FROM Education e ORDER BY e.startDate DESC NULLS LAST")
    Page<Education> findAllOrderByStartDateDescNullsLast(Pageable pageable);
}
