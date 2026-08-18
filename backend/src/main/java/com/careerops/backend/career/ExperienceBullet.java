package com.careerops.backend.career;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import java.time.Instant;

@Entity
@Table(name = "experience_bullets")
public class ExperienceBullet {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "career_experience_id", nullable = false) private CareerExperience careerExperience;
    @Enumerated(EnumType.STRING) @Column(name = "bullet_type", length = 50) private BulletType bulletType;
    @Column(nullable = false, length = 1000) private String content;
    @Column(name = "sort_order", nullable = false) private Integer sortOrder;
    @CreationTimestamp private Instant createdAt;
    @UpdateTimestamp private Instant updatedAt;
    protected ExperienceBullet() {}
    public ExperienceBullet(CareerExperience careerExperience, BulletType bulletType, String content, Integer sortOrder) {
        this.careerExperience = careerExperience; this.bulletType = bulletType; this.content = content; this.sortOrder = sortOrder;
    }
    public Long getId() { return id; }
    public CareerExperience getCareerExperience() { return careerExperience; }
    public BulletType getBulletType() { return bulletType; }
    public String getContent() { return content; }
    public Integer getSortOrder() { return sortOrder; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void updateBulletType(BulletType value) { bulletType = value; }
    public void updateContent(String value) { content = value; }
    public void updateSortOrder(Integer value) { sortOrder = value; }
}
