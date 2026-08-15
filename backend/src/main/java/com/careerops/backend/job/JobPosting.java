package com.careerops.backend.job;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "job_postings")
public class JobPosting {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String companyName;
    private String title;
    private String employmentType;
    private String careerLevel;
    private String educationRequirement;
    private String status;
    private String institutionCode;
    private String jobCategory;
    private String location;
    private LocalDate applicationStartAt;
    private LocalDate applicationEndAt;
    private String source;
    private String sourceUrl;
    private String externalId;

    @CreationTimestamp
    private Instant createdAt;

    protected JobPosting() {
    }

    public JobPosting(
            String companyName,
            String title,
            String employmentType,
            String careerLevel,
            String educationRequirement,
            String status,
            String institutionCode,
            String jobCategory,
            String location,
            LocalDate applicationStartAt,
            LocalDate applicationEndAt,
            String source,
            String sourceUrl,
            String externalId
    ) {
        this.companyName = companyName;
        this.title = title;
        this.employmentType = employmentType;
        this.careerLevel = careerLevel;
        this.educationRequirement = educationRequirement;
        this.status = status;
        this.institutionCode = institutionCode;
        this.jobCategory = jobCategory;
        this.location = location;
        this.applicationStartAt = applicationStartAt;
        this.applicationEndAt = applicationEndAt;
        this.source = source;
        this.sourceUrl = sourceUrl;
        this.externalId = externalId;
    }

    public Long getId() { return id; }
    public String getCompanyName() { return companyName; }
    public String getTitle() { return title; }
    public String getEmploymentType() { return employmentType; }
    public String getCareerLevel() { return careerLevel; }
    public String getEducationRequirement() { return educationRequirement; }
    public String getStatus() { return status; }
    public String getInstitutionCode() { return institutionCode; }
    public String getJobCategory() { return jobCategory; }
    public String getLocation() { return location; }
    public LocalDate getApplicationStartAt() { return applicationStartAt; }
    public LocalDate getApplicationEndAt() { return applicationEndAt; }
    public String getSource() { return source; }
    public String getSourceUrl() { return sourceUrl; }
    public String getExternalId() { return externalId; }
    public Instant getCreatedAt() { return createdAt; }

    public void updateStatus(String status) {
        this.status = status;
    }
}
