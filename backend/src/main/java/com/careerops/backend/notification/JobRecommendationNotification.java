package com.careerops.backend.notification;

import com.careerops.backend.job.JobPosting;
import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

@Entity
@Table(name = "job_recommendation_notifications")
public class JobRecommendationNotification {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "job_posting_id", nullable = false) private JobPosting jobPosting;
    @Column(name = "recommendation_score", nullable = false) private double recommendationScore;
    @Column(nullable = false, length = 200) private String reason;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) private NotificationStatus status;
    @CreationTimestamp @Column(nullable = false) private Instant createdAt;

    protected JobRecommendationNotification() {}

    public JobRecommendationNotification(JobPosting jobPosting, double recommendationScore, String reason) {
        this(jobPosting, recommendationScore, reason, NotificationStatus.PENDING);
    }

    JobRecommendationNotification(JobPosting jobPosting, double recommendationScore, String reason, NotificationStatus status) {
        this.jobPosting = jobPosting;
        this.recommendationScore = recommendationScore;
        this.reason = reason;
        this.status = status;
    }

    public Long getId() { return id; }
    public JobPosting getJobPosting() { return jobPosting; }
    public double getRecommendationScore() { return recommendationScore; }
    public String getReason() { return reason; }
    public NotificationStatus getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
}
