package com.careerops.backend.job;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import java.time.Instant;

@Entity
@Table(name = "recruitment_steps", uniqueConstraints = @UniqueConstraint(name = "uk_recruitment_steps_recrut_step_sn", columnNames = "recrut_step_sn"))
public class RecruitmentStep {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(optional = false, fetch = FetchType.LAZY) @JoinColumn(name = "job_posting_id", nullable = false) private JobPosting jobPosting;
    @Column(nullable = false) private Long recrutStepSn;
    private Integer sortNo;
    private Long minStepSn;
    private Long maxStepSn;
    private String stepGroupName;
    private Double competitionRate;
    private Integer applicantCount;
    private Integer recruitCount;
    private String occurredAtRaw;
    @CreationTimestamp private Instant createdAt;

    protected RecruitmentStep() {}

    public RecruitmentStep(JobPosting jobPosting, Long recrutStepSn, Integer sortNo, Long minStepSn, Long maxStepSn,
                           String stepGroupName, Double competitionRate, Integer applicantCount,
                           Integer recruitCount, String occurredAtRaw) {
        this.jobPosting = jobPosting; this.recrutStepSn = recrutStepSn; this.sortNo = sortNo;
        this.minStepSn = minStepSn; this.maxStepSn = maxStepSn; this.stepGroupName = stepGroupName;
        this.competitionRate = competitionRate; this.applicantCount = applicantCount;
        this.recruitCount = recruitCount; this.occurredAtRaw = occurredAtRaw;
    }

    public Long getId() { return id; }
    public JobPosting getJobPosting() { return jobPosting; }
    public Long getRecrutStepSn() { return recrutStepSn; }
    public Integer getSortNo() { return sortNo; }
    public Long getMinStepSn() { return minStepSn; }
    public Long getMaxStepSn() { return maxStepSn; }
    public String getStepGroupName() { return stepGroupName; }
    public Double getCompetitionRate() { return competitionRate; }
    public Integer getApplicantCount() { return applicantCount; }
    public Integer getRecruitCount() { return recruitCount; }
    public String getOccurredAtRaw() { return occurredAtRaw; }
    public Instant getCreatedAt() { return createdAt; }
}
