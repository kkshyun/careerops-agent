package com.careerops.backend.career;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "career_certifications")
public class Certification {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(nullable = false, length = 200) private String name;
    @Column(length = 200) private String issuer;
    private LocalDate acquiredDate;
    private LocalDate expirationDate;
    @Column(name = "credential_id", length = 100) private String credentialId;
    @Column(length = 2000) private String description;
    @CreationTimestamp private Instant createdAt;
    @UpdateTimestamp private Instant updatedAt;

    protected Certification() {}
    public Certification(String name, String issuer, LocalDate acquiredDate, LocalDate expirationDate,
                         String credentialId, String description) {
        this.name = name; this.issuer = issuer; this.acquiredDate = acquiredDate;
        this.expirationDate = expirationDate; this.credentialId = credentialId; this.description = description;
    }
    public Long getId() { return id; }
    public String getName() { return name; }
    public String getIssuer() { return issuer; }
    public LocalDate getAcquiredDate() { return acquiredDate; }
    public LocalDate getExpirationDate() { return expirationDate; }
    public String getCredentialId() { return credentialId; }
    public String getDescription() { return description; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void updateName(String value) { name = value; }
    public void updateIssuer(String value) { issuer = value; }
    public void updateAcquiredDate(LocalDate value) { acquiredDate = value; }
    public void updateExpirationDate(LocalDate value) { expirationDate = value; }
    public void updateCredentialId(String value) { credentialId = value; }
    public void updateDescription(String value) { description = value; }
}
