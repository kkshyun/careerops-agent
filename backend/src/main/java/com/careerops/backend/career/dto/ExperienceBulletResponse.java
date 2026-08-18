package com.careerops.backend.career.dto;

import com.careerops.backend.career.BulletType;
import com.careerops.backend.career.ExperienceBullet;

public record ExperienceBulletResponse(BulletType bulletType, String content, Integer sortOrder) {
    public static ExperienceBulletResponse from(ExperienceBullet entity) {
        return new ExperienceBulletResponse(entity.getBulletType(), entity.getContent(), entity.getSortOrder());
    }
}
