package com.careerops.backend.notification;

import com.careerops.backend.notification.dto.JobRecommendationNotificationResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.time.Instant;

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

    @Query("SELECT n.id FROM JobRecommendationNotification n WHERE n.status = :status " +
            "ORDER BY n.createdAt ASC, n.id ASC")
    List<Long> findIdsByStatusOrderByCreatedAtAsc(
            @Param("status") NotificationStatus status, Pageable pageable);

    boolean existsByJobPostingId(Long jobPostingId);
    long countByJobPostingId(Long jobPostingId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
            UPDATE job_recommendation_notifications
            SET status = 'SENDING', last_attempt_at = :attemptedAt, failure_code = NULL
            WHERE id = :id AND status IN ('PENDING', 'FAILED')
            """, nativeQuery = true)
    int claimForSending(@Param("id") long id, @Param("attemptedAt") Instant attemptedAt);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
            UPDATE job_recommendation_notifications
            SET status = 'SENT', sent_at = :sentAt, failure_code = NULL
            WHERE id = :id AND status = 'SENDING'
            """, nativeQuery = true)
    int markSent(@Param("id") long id, @Param("sentAt") Instant sentAt);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
            UPDATE job_recommendation_notifications
            SET status = 'FAILED', failure_code = :failureCode
            WHERE id = :id AND status = 'SENDING'
            """, nativeQuery = true)
    int markFailed(@Param("id") long id, @Param("failureCode") String failureCode);

    @Query("""
            SELECT new com.careerops.backend.notification.NotificationSendSnapshot(
                n.id, j.id, j.companyName, j.title, j.applicationEndAt, j.sourceUrl,
                n.recommendationScore, n.reason, n.status, n.sentAt)
            FROM JobRecommendationNotification n JOIN n.jobPosting j WHERE n.id = :id
            """)
    Optional<NotificationSendSnapshot> findSnapshotById(@Param("id") long id);
}
