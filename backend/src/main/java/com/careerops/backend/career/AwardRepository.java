package com.careerops.backend.career;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface AwardRepository extends JpaRepository<Award, Long> {
    @Query("SELECT a FROM Award a ORDER BY a.awardedDate DESC NULLS LAST")
    Page<Award> findAllOrderByAwardedDateDescNullsLast(Pageable pageable);
}
