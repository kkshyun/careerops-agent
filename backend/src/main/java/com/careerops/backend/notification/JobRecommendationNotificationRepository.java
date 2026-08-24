package com.careerops.backend.notification;

import com.careerops.backend.notification.dto.JobRecommendationNotificationResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface JobRecommendationNotificationRepository extends JpaRepository<JobRecommendationNotification, Long> {
    @Query("SELECT n.jobPosting.id FROM JobRecommendationNotification n WHERE n.jobPosting.id IN :jobIds")
    List<Long> findExistingJobPostingIds(@Param("jobIds") Collection<Long> jobIds);

    @Query("""
            SELECT new com.careerops.backend.notification.dto.JobRecommendationNotificationResponse(
                n.id, j.id, j.companyName, j.title, j.applicationEndAt,
                n.recommendationScore, n.reason, n.status, n.createdAt)
            FROM JobRecommendationNotification n JOIN n.jobPosting j
            WHERE (:status IS NULL OR n.status = :status)
            ORDER BY n.createdAt DESC, n.id DESC
            """)
    Page<JobRecommendationNotificationResponse> search(
            @Param("status") NotificationStatus status, Pageable pageable);

    boolean existsByJobPostingId(Long jobPostingId);
    long countByJobPostingId(Long jobPostingId);
}
