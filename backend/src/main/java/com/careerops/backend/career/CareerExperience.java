package com.careerops.backend.career;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "career_experiences")
public class CareerExperience {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 50) private ExperienceType type;
    @Column(nullable = false, length = 200) private String title;
    @Column(length = 200) private String organization;
    @Column(length = 200) private String role;
    private LocalDate startDate;
    private LocalDate endDate;
    @Column(length = 500) private String summary;
    @Column(length = 4000) private String detail;
    @Enumerated(EnumType.STRING) @Column(name = "source_type", nullable = false, length = 20) private SourceType sourceType;
    @Column(name = "source_import_candidate_id") private Long sourceImportCandidateId;
    @CreationTimestamp private Instant createdAt;
    @UpdateTimestamp private Instant updatedAt;

    protected CareerExperience() {}
    public CareerExperience(ExperienceType type, String title, String organization, String role,
                            LocalDate startDate, LocalDate endDate, String summary, String detail) {
        this.type = type; this.title = title; this.organization = organization; this.role = role;
        this.startDate = startDate; this.endDate = endDate; this.summary = summary; this.detail = detail;
        this.sourceType = SourceType.MANUAL;
    }
    public CareerExperience(ExperienceType type, String title, String organization, String role,
                            LocalDate startDate, LocalDate endDate, String summary, String detail,
                            SourceType sourceType, Long sourceImportCandidateId) {
        this(type, title, organization, role, startDate, endDate, summary, detail);
        this.sourceType = sourceType; this.sourceImportCandidateId = sourceImportCandidateId;
    }
    public Long getId() { return id; }
    public ExperienceType getType() { return type; }
    public String getTitle() { return title; }
    public String getOrganization() { return organization; }
    public String getRole() { return role; }
    public LocalDate getStartDate() { return startDate; }
    public LocalDate getEndDate() { return endDate; }
    public String getSummary() { return summary; }
    public String getDetail() { return detail; }
    public SourceType getSourceType() { return sourceType; }
    public Long getSourceImportCandidateId() { return sourceImportCandidateId; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void updateType(ExperienceType value) { type = value; }
    public void updateTitle(String value) { title = value; }
    public void updateOrganization(String value) { organization = value; }
    public void updateRole(String value) { role = value; }
    public void updateStartDate(LocalDate value) { startDate = value; }
    public void updateEndDate(LocalDate value) { endDate = value; }
    public void updateSummary(String value) { summary = value; }
    public void updateDetail(String value) { detail = value; }
}
