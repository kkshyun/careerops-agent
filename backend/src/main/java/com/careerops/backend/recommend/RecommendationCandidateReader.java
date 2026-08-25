package com.careerops.backend.recommend;

import com.careerops.backend.career.*;
import com.careerops.backend.job.*;
import com.careerops.backend.pkbimport.*;
import com.careerops.backend.recommend.dto.*;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Component
public class RecommendationCandidateReader {
    private final JobPostingRepository jobs;
    private final CareerExperienceRepository experiences;
    private final ExperienceTagRepository tags;
    private final CertificationRepository certifications;
    private final EducationRepository educations;
    private final AwardRepository awards;
    private final ImportCandidateRepository importCandidates;

    public RecommendationCandidateReader(JobPostingRepository jobs, CareerExperienceRepository experiences,
            ExperienceTagRepository tags, CertificationRepository certifications, EducationRepository educations,
            AwardRepository awards, ImportCandidateRepository importCandidates) {
        this.jobs = jobs; this.experiences = experiences; this.tags = tags; this.certifications = certifications;
        this.educations = educations; this.awards = awards; this.importCandidates = importCandidates;
    }

    @Transactional(readOnly = true)
    public RecommendationInput read() {
        Set<Long> approved = importCandidates.findAll().stream()
                .filter(v -> v.getStatus() == ImportCandidateStatus.APPROVED)
                .map(ImportCandidate::getId).collect(Collectors.toSet());
        List<CareerExperience> exps = experiences.findAll().stream()
                .filter(v -> approved(v.getSourceType(), v.getSourceImportCandidateId(), approved)).toList();
        List<Certification> certs = certifications.findAll().stream()
                .filter(v -> approved(v.getSourceType(), v.getSourceImportCandidateId(), approved)).toList();
        List<Education> edus = educations.findAll().stream()
                .filter(v -> approved(v.getSourceType(), v.getSourceImportCandidateId(), approved)).toList();
        List<Award> awardList = awards.findAll().stream()
                .filter(v -> approved(v.getSourceType(), v.getSourceImportCandidateId(), approved)).toList();
        List<Long> expIds = exps.stream().map(CareerExperience::getId).toList();
        Map<Long, List<String>> tagMap = (expIds.isEmpty() ? List.<ExperienceTag>of() : tags.findByCareerExperienceIdIn(expIds))
                .stream().collect(Collectors.groupingBy(v -> v.getCareerExperience().getId(),
                        Collectors.mapping(ExperienceTag::getKeyword, Collectors.toList())));
        return new RecommendationInput(
                jobs.findAllByStatus("OPEN").stream().map(v -> new RecommendationJobCandidate(v.getId(),
                        v.getCompanyName(), v.getTitle(), v.getJobCategory(), v.getCareerLevel(),
                        v.getEducationRequirement(), v.getApplicationEndAt())).toList(),
                exps.stream().map(v -> new RecommendationExperience(v.getId(), v.getTitle(), v.getOrganization(),
                        v.getRole(), v.getSummary(), List.copyOf(tagMap.getOrDefault(v.getId(), List.of())))).toList(),
                certs.stream().map(v -> new RecommendationCertification(v.getId(), v.getName(), v.getIssuer())).toList(),
                edus.stream().map(v -> new RecommendationEducation(v.getId(), v.getInstitution(), v.getMajor(),
                        text(v.getDegree()), text(v.getStatus()))).toList(),
                awardList.stream().map(v -> new RecommendationAward(v.getId(), v.getTitle(), v.getIssuer())).toList());
    }

    private boolean approved(SourceType source, Long candidateId, Set<Long> approved) {
        return source == SourceType.MANUAL || (source == SourceType.IMPORT && candidateId != null && approved.contains(candidateId));
    }

    private String text(Object value) { return value == null ? null : value.toString(); }
}
