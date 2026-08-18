package com.careerops.backend.application;

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
import java.time.LocalDateTime;

@Entity
@Table(name = "application_stages")
public class ApplicationStage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "job_application_id", nullable = false)
    private JobApplication jobApplication;

    @Enumerated(EnumType.STRING)
    @Column(name = "stage_type", nullable = false, length = 50)
    private StageType stageType;

    @Column(length = 100)
    private String label;

    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder;

    @Column(name = "scheduled_at")
    private LocalDateTime scheduledAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private StageResult result;

    @Column(length = 1000)
    private String memo;

    @CreationTimestamp
    private Instant createdAt;

    @UpdateTimestamp
    private Instant updatedAt;

    protected ApplicationStage() {
    }

    public ApplicationStage(JobApplication jobApplication, StageType stageType, String label,
                            Integer sortOrder, LocalDateTime scheduledAt, StageResult result, String memo) {
        this.jobApplication = jobApplication;
        this.stageType = stageType;
        this.label = label;
        this.sortOrder = sortOrder;
        this.scheduledAt = scheduledAt;
        this.result = result;
        this.memo = memo;
    }

    public Long getId() { return id; }
    public JobApplication getJobApplication() { return jobApplication; }
    public StageType getStageType() { return stageType; }
    public String getLabel() { return label; }
    public Integer getSortOrder() { return sortOrder; }
    public LocalDateTime getScheduledAt() { return scheduledAt; }
    public StageResult getResult() { return result; }
    public String getMemo() { return memo; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    public void updateLabel(String label) { this.label = label; }
    public void updateSortOrder(Integer sortOrder) { this.sortOrder = sortOrder; }
    public void updateScheduledAt(LocalDateTime scheduledAt) { this.scheduledAt = scheduledAt; }
    public void updateResult(StageResult result) { this.result = result; }
    public void updateMemo(String memo) { this.memo = memo; }
}
