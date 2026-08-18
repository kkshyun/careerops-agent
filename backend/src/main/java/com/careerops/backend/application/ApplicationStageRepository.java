package com.careerops.backend.application;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ApplicationStageRepository extends JpaRepository<ApplicationStage, Long> {

    List<ApplicationStage> findByJobApplicationIdOrderBySortOrderAsc(Long jobApplicationId);

    Optional<ApplicationStage> findByIdAndJobApplicationId(Long id, Long jobApplicationId);

    Optional<ApplicationStage> findTopByJobApplicationIdOrderBySortOrderDesc(Long jobApplicationId);
}
