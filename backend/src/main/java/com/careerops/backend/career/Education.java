package com.careerops.backend.career;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "career_educations")
public class Education {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(nullable = false, length = 200) private String institution;
    @Column(length = 200) private String major;
    @Enumerated(EnumType.STRING) @Column(length = 50) private EducationDegree degree;
    @Enumerated(EnumType.STRING) @Column(length = 50) private EducationStatus status;
    private LocalDate startDate;
    private LocalDate endDate;
    @Column(precision = 5, scale = 2) private BigDecimal gpa;
    @Column(name = "gpa_scale", precision = 5, scale = 2) private BigDecimal gpaScale;
    @Column(length = 2000) private String description;
    @Enumerated(EnumType.STRING) @Column(name = "source_type", nullable = false, length = 20) private SourceType sourceType;
    @Column(name = "source_import_candidate_id") private Long sourceImportCandidateId;
    @CreationTimestamp private Instant createdAt;
    @UpdateTimestamp private Instant updatedAt;

    protected Education() {}
    public Education(String institution, String major, EducationDegree degree, EducationStatus status,
                     LocalDate startDate, LocalDate endDate, BigDecimal gpa, BigDecimal gpaScale,
                     String description) {
        this.institution = institution; this.major = major; this.degree = degree; this.status = status;
        this.startDate = startDate; this.endDate = endDate; this.gpa = gpa; this.gpaScale = gpaScale;
        this.description = description;
        this.sourceType = SourceType.MANUAL;
    }
    public Education(String institution, String major, EducationDegree degree, EducationStatus status,
                     LocalDate startDate, LocalDate endDate, BigDecimal gpa, BigDecimal gpaScale,
                     String description, SourceType sourceType, Long sourceImportCandidateId) {
        this(institution, major, degree, status, startDate, endDate, gpa, gpaScale, description);
        this.sourceType = sourceType; this.sourceImportCandidateId = sourceImportCandidateId;
    }
    public Long getId() { return id; }
    public String getInstitution() { return institution; }
    public String getMajor() { return major; }
    public EducationDegree getDegree() { return degree; }
    public EducationStatus getStatus() { return status; }
    public LocalDate getStartDate() { return startDate; }
    public LocalDate getEndDate() { return endDate; }
    public BigDecimal getGpa() { return gpa; }
    public BigDecimal getGpaScale() { return gpaScale; }
    public String getDescription() { return description; }
    public SourceType getSourceType() { return sourceType; }
    public Long getSourceImportCandidateId() { return sourceImportCandidateId; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void updateInstitution(String value) { institution = value; }
    public void updateMajor(String value) { major = value; }
    public void updateDegree(EducationDegree value) { degree = value; }
    public void updateStatus(EducationStatus value) { status = value; }
    public void updateStartDate(LocalDate value) { startDate = value; }
    public void updateEndDate(LocalDate value) { endDate = value; }
    public void updateGpa(BigDecimal value) { gpa = value; }
    public void updateGpaScale(BigDecimal value) { gpaScale = value; }
    public void updateDescription(String value) { description = value; }
}
