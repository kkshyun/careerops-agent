package com.careerops.backend.career;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import java.time.Instant;

@Entity
@Table(name = "experience_tags")
public class ExperienceTag {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "career_experience_id", nullable = false) private CareerExperience careerExperience;
    @Column(nullable = false, length = 50) private String keyword;
    @CreationTimestamp private Instant createdAt;
    protected ExperienceTag() {}
    public ExperienceTag(CareerExperience careerExperience, String keyword) { this.careerExperience = careerExperience; this.keyword = keyword; }
    public Long getId() { return id; }
    public CareerExperience getCareerExperience() { return careerExperience; }
    public String getKeyword() { return keyword; }
    public Instant getCreatedAt() { return createdAt; }
    public void updateKeyword(String value) { keyword = value; }
}
