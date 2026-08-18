package com.careerops.backend.application;

import com.careerops.backend.job.JobPosting;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "job_applications")
public class JobApplication {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "job_posting_id", nullable = false)
    private JobPosting jobPosting;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private ApplicationStatus status;

    @Column(length = 2000)
    private String memo;

    private LocalDate appliedAt;

    @CreationTimestamp
    private Instant createdAt;

    @UpdateTimestamp
    private Instant updatedAt;

    protected JobApplication() {
    }

    public JobApplication(JobPosting jobPosting, ApplicationStatus status, String memo, LocalDate appliedAt) {
        this.jobPosting = jobPosting;
        this.status = status;
        this.memo = memo;
        this.appliedAt = appliedAt;
    }

    public Long getId() { return id; }
    public JobPosting getJobPosting() { return jobPosting; }
    public ApplicationStatus getStatus() { return status; }
    public String getMemo() { return memo; }
    public LocalDate getAppliedAt() { return appliedAt; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    public void updateStatus(ApplicationStatus status) { this.status = status; }
    public void updateMemo(String memo) { this.memo = memo; }
    public void updateAppliedAt(LocalDate appliedAt) { this.appliedAt = appliedAt; }
}
