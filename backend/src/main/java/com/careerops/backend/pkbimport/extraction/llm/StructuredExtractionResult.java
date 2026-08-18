package com.careerops.backend.pkbimport.extraction.llm;

import com.careerops.backend.career.dto.AwardCreateRequest;
import com.careerops.backend.career.dto.CareerExperienceCreateRequest;
import com.careerops.backend.career.dto.CertificationCreateRequest;
import com.careerops.backend.career.dto.EducationCreateRequest;

import java.util.List;

public record StructuredExtractionResult(
        List<CareerExperienceCreateRequest> careerExperiences,
        List<CertificationCreateRequest> certifications,
        List<EducationCreateRequest> educations,
        List<AwardCreateRequest> awards) {

    public StructuredExtractionResult {
        careerExperiences = careerExperiences == null ? List.of() : List.copyOf(careerExperiences);
        certifications = certifications == null ? List.of() : List.copyOf(certifications);
        educations = educations == null ? List.of() : List.copyOf(educations);
        awards = awards == null ? List.of() : List.copyOf(awards);
    }
}
