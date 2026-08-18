package com.careerops.backend.pkbimport.extraction.llm;

import com.careerops.backend.career.dto.*;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;
import java.util.Set;

@Component
public class PlaceholderValueSanitizer {
    private static final Set<String> PLACEHOLDERS = Set.of(
            "현재", "미상", "해당없음", "해당 없음", "n/a", "unknown", "없음");

    public StructuredExtractionResult sanitize(StructuredExtractionResult result) {
        return new StructuredExtractionResult(
                result.careerExperiences().stream().map(this::sanitize).toList(),
                result.certifications().stream().map(this::sanitize).toList(),
                result.educations().stream().map(this::sanitize).toList(),
                result.awards().stream().map(this::sanitize).toList());
    }

    private CareerExperienceCreateRequest sanitize(CareerExperienceCreateRequest value) {
        List<ExperienceBulletRequest> bullets = value.bullets() == null ? null : value.bullets().stream()
                .map(b -> new ExperienceBulletRequest(b.bulletType(), sanitize(b.content()))).toList();
        List<String> tags = value.tags() == null ? null : value.tags().stream().map(this::sanitize).toList();
        return new CareerExperienceCreateRequest(value.type(), sanitize(value.title()),
                sanitize(value.organization()), sanitize(value.role()), value.startDate(), value.endDate(),
                sanitize(value.summary()), sanitize(value.detail()), bullets, tags);
    }

    private CertificationCreateRequest sanitize(CertificationCreateRequest value) {
        return new CertificationCreateRequest(sanitize(value.name()), sanitize(value.issuer()),
                value.acquiredDate(), value.expirationDate(), sanitize(value.credentialId()),
                sanitize(value.description()));
    }

    private EducationCreateRequest sanitize(EducationCreateRequest value) {
        return new EducationCreateRequest(sanitize(value.institution()), sanitize(value.major()),
                value.degree(), value.status(), value.startDate(), value.endDate(), value.gpa(), value.gpaScale(),
                sanitize(value.description()));
    }

    private AwardCreateRequest sanitize(AwardCreateRequest value) {
        return new AwardCreateRequest(sanitize(value.title()), sanitize(value.issuer()), value.awardedDate(),
                sanitize(value.description()));
    }

    private String sanitize(String value) {
        if (value == null) return null;
        return PLACEHOLDERS.contains(value.trim().toLowerCase(Locale.ROOT)) ? null : value;
    }
}
