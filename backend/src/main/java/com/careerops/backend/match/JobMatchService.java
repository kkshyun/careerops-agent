package com.careerops.backend.match;

import com.careerops.backend.career.AwardRepository;
import com.careerops.backend.career.CareerExperience;
import com.careerops.backend.career.CareerExperienceRepository;
import com.careerops.backend.career.CertificationRepository;
import com.careerops.backend.career.EducationRepository;
import com.careerops.backend.career.ExperienceTag;
import com.careerops.backend.career.ExperienceTagRepository;
import com.careerops.backend.job.JobPosting;
import com.careerops.backend.job.JobPostingRepository;
import com.careerops.backend.match.dto.JobMatchResponse;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.http.HttpStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class JobMatchService {
    private final JobPostingRepository jobPostingRepository;
    private final CareerExperienceRepository experienceRepository;
    private final ExperienceTagRepository tagRepository;
    private final CertificationRepository certificationRepository;
    private final EducationRepository educationRepository;
    private final AwardRepository awardRepository;
    private final CareerMatchEngine matchEngine;
    private final Clock clock;
    private final Counter successCounter;
    private final Counter notFoundCounter;
    private final Timer duration;
    private final DistributionSummary scoreSummary;

    @Autowired
    public JobMatchService(JobPostingRepository jobPostingRepository,
                           CareerExperienceRepository experienceRepository,
                           ExperienceTagRepository tagRepository,
                           CertificationRepository certificationRepository,
                           EducationRepository educationRepository,
                           AwardRepository awardRepository,
                           CareerMatchEngine matchEngine,
                           MeterRegistry meterRegistry) {
        this(jobPostingRepository, experienceRepository, tagRepository, certificationRepository,
                educationRepository, awardRepository, matchEngine, meterRegistry, Clock.systemUTC());
    }

    JobMatchService(JobPostingRepository jobPostingRepository,
                    CareerExperienceRepository experienceRepository,
                    ExperienceTagRepository tagRepository,
                    CertificationRepository certificationRepository,
                    EducationRepository educationRepository,
                    AwardRepository awardRepository,
                    CareerMatchEngine matchEngine,
                    MeterRegistry meterRegistry,
                    Clock clock) {
        this.jobPostingRepository = jobPostingRepository;
        this.experienceRepository = experienceRepository;
        this.tagRepository = tagRepository;
        this.certificationRepository = certificationRepository;
        this.educationRepository = educationRepository;
        this.awardRepository = awardRepository;
        this.matchEngine = matchEngine;
        this.clock = clock;
        this.successCounter = Counter.builder("careerops.match.request").tag("result", "success").register(meterRegistry);
        this.notFoundCounter = Counter.builder("careerops.match.request").tag("result", "not_found").register(meterRegistry);
        this.duration = Timer.builder("careerops.match.duration").register(meterRegistry);
        this.scoreSummary = DistributionSummary.builder("careerops.match.score").register(meterRegistry);
    }

    @Transactional(readOnly = true)
    public JobMatchResponse match(Long jobId) {
        return duration.record(() -> calculate(jobId));
    }

    private JobMatchResponse calculate(Long jobId) {
        JobPosting posting = jobPostingRepository.findById(jobId).orElse(null);
        if (posting == null) {
            notFoundCounter.increment();
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        List<CareerExperience> experiences = experienceRepository.findAll();
        List<Long> experienceIds = experiences.stream().map(CareerExperience::getId).toList();
        List<ExperienceTag> tags = experienceIds.isEmpty() ? List.of() : tagRepository.findByCareerExperienceIdIn(experienceIds);
        Map<Long, List<ExperienceTag>> tagsByExperience = tags.stream()
                .collect(Collectors.groupingBy(tag -> tag.getCareerExperience().getId()));

        CareerMatchEngine.MatchResult result = matchEngine.calculate(posting.getJobCategory(), experiences,
                tagsByExperience, certificationRepository.findAll(), educationRepository.findAll(), awardRepository.findAll());
        successCounter.increment();
        scoreSummary.record(result.overallScore());
        return new JobMatchResponse(posting.getId(), result.overallScore(), result.experiences(),
                result.certifications(), result.educations(), result.awards(), result.unmatchedJobCategories(),
                posting.getCareerLevel(), posting.getEducationRequirement(), Instant.now(clock));
    }
}
