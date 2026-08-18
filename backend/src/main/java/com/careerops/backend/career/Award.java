package com.careerops.backend.career;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "career_awards")
public class Award {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(nullable = false, length = 200) private String title;
    @Column(length = 200) private String issuer;
    private LocalDate awardedDate;
    @Column(length = 2000) private String description;
    @CreationTimestamp private Instant createdAt;
    @UpdateTimestamp private Instant updatedAt;

    protected Award() {}
    public Award(String title, String issuer, LocalDate awardedDate, String description) {
        this.title = title; this.issuer = issuer; this.awardedDate = awardedDate;
        this.description = description;
    }
    public Long getId() { return id; }
    public String getTitle() { return title; }
    public String getIssuer() { return issuer; }
    public LocalDate getAwardedDate() { return awardedDate; }
    public String getDescription() { return description; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void updateTitle(String value) { title = value; }
    public void updateIssuer(String value) { issuer = value; }
    public void updateAwardedDate(LocalDate value) { awardedDate = value; }
    public void updateDescription(String value) { description = value; }
}
