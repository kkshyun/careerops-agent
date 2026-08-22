package com.careerops.backend.match;

import com.careerops.backend.career.*;
import com.careerops.backend.job.JobPosting;
import com.careerops.backend.job.JobPostingRepository;
import com.careerops.backend.match.dto.*;
import com.careerops.backend.match.semantic.SemanticJobMatchClient;
import com.careerops.backend.match.semantic.SemanticMatchException;
import com.careerops.backend.match.semantic.dto.*;
import io.micrometer.core.instrument.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.time.Clock;
import java.time.Instant;
import java.util.*;
import java.util.function.*;
import java.util.stream.Collectors;

@Service
public class SemanticJobMatchService {
    private static final Logger log = LoggerFactory.getLogger(SemanticJobMatchService.class);
    private static final Comparator<SemanticMatchEvidence> ORDER = Comparator
            .comparingDouble(SemanticMatchEvidence::score).reversed().thenComparing(SemanticMatchEvidence::id);
    private final JobPostingRepository jobs; private final CareerExperienceRepository experiences;
    private final ExperienceTagRepository tags; private final CertificationRepository certifications;
    private final EducationRepository educations; private final AwardRepository awards;
    private final CareerMatchEngine deterministicEngine; private final SemanticJobMatchClient client;
    private final Clock clock; private final io.micrometer.core.instrument.Timer duration; private final DistributionSummary score;
    private final Map<String, Counter> counters;

    @Autowired
    public SemanticJobMatchService(JobPostingRepository jobs, CareerExperienceRepository experiences,
            ExperienceTagRepository tags, CertificationRepository certifications, EducationRepository educations,
            AwardRepository awards, CareerMatchEngine deterministicEngine, SemanticJobMatchClient client,
            MeterRegistry registry) {
        this(jobs, experiences, tags, certifications, educations, awards, deterministicEngine, client, registry, Clock.systemUTC());
    }

    SemanticJobMatchService(JobPostingRepository jobs, CareerExperienceRepository experiences,
            ExperienceTagRepository tags, CertificationRepository certifications, EducationRepository educations,
            AwardRepository awards, CareerMatchEngine deterministicEngine, SemanticJobMatchClient client,
            MeterRegistry registry, Clock clock) {
        this.jobs=jobs; this.experiences=experiences; this.tags=tags; this.certifications=certifications;
        this.educations=educations; this.awards=awards; this.deterministicEngine=deterministicEngine; this.client=client; this.clock=clock;
        this.duration=io.micrometer.core.instrument.Timer.builder("careerops.semantic-match.duration").register(registry);
        this.score=DistributionSummary.builder("careerops.semantic-match.score").register(registry);
        this.counters=new HashMap<>();
        for (String result : List.of("success", "job_not_found", "provider_error", "validation_failed"))
            counters.put(result, Counter.builder("careerops.semantic-match.request").tag("result", result).register(registry));
    }

    @Transactional(readOnly = true)
    public SemanticJobMatchResponse match(Long jobId) { return duration.record(() -> calculate(jobId)); }

    private SemanticJobMatchResponse calculate(Long jobId) {
        JobPosting job = jobs.findById(jobId).orElse(null);
        if (job == null) { counters.get("job_not_found").increment(); throw new ResponseStatusException(HttpStatus.NOT_FOUND); }
        List<CareerExperience> experienceList=experiences.findAll(); List<Certification> certificationList=certifications.findAll();
        List<Education> educationList=educations.findAll(); List<Award> awardList=awards.findAll();
        if (experienceList.isEmpty() && certificationList.isEmpty() && educationList.isEmpty() && awardList.isEmpty()) {
            return success(new SemanticJobMatchResponse(job.getId(), 0, 0, List.of(), List.of(), List.of(), List.of(), List.of(), Instant.now(clock)));
        }
        List<Long> ids=experienceList.stream().map(CareerExperience::getId).toList();
        Map<Long,List<ExperienceTag>> tagMap=(ids.isEmpty()?List.<ExperienceTag>of():tags.findByCareerExperienceIdIn(ids)).stream()
                .collect(Collectors.groupingBy(tag -> tag.getCareerExperience().getId()));
        double deterministic=deterministicEngine.calculate(job.getJobCategory(), experienceList, tagMap,
                certificationList, educationList, awardList).overallScore();
        try {
            RawSemanticMatchResult raw=client.match(job, experienceList, tagMap, certificationList, educationList, awardList);
            validateScore(raw.semanticScore());
            List<SemanticMatchEvidence> experienceMatches=convert(raw.experienceMatches(), experienceList, CareerExperience::getId,
                    CareerExperience::getTitle, RawExperienceMatch::id, RawExperienceMatch::score, RawExperienceMatch::evidence, RawExperienceMatch::reason, "CAREER_EXPERIENCE", 5);
            List<SemanticMatchEvidence> certificationMatches=convert(raw.certificationMatches(), certificationList, Certification::getId,
                    Certification::getName, RawCertificationMatch::id, RawCertificationMatch::score, RawCertificationMatch::evidence, RawCertificationMatch::reason, "CERTIFICATION", 3);
            List<SemanticMatchEvidence> educationMatches=convert(raw.educationMatches(), educationList, Education::getId,
                    education -> education.getMajor() == null || education.getMajor().isBlank()
                            ? education.getInstitution() : education.getMajor(),
                    RawEducationMatch::id, RawEducationMatch::score, RawEducationMatch::evidence, RawEducationMatch::reason, "EDUCATION", 3);
            List<SemanticMatchEvidence> awardMatches=convert(raw.awardMatches(), awardList, Award::getId,
                    Award::getTitle, RawAwardMatch::id, RawAwardMatch::score, RawAwardMatch::evidence, RawAwardMatch::reason, "AWARD", 3);
            return success(new SemanticJobMatchResponse(job.getId(), deterministic, raw.semanticScore(), experienceMatches,
                    certificationMatches, educationMatches, awardMatches, safe(raw.gaps()), Instant.now(clock)));
        } catch (SemanticMatchException exception) {
            counters.get(exception.isValidationFailure() ? "validation_failed" : "provider_error").increment();
            log.warn("Semantic match failed reason={}", exception.reason());
            throw exception;
        } catch (RuntimeException exception) {
            counters.get("provider_error").increment();
            throw new SemanticMatchException(SemanticMatchException.Reason.MALFORMED_RESPONSE, exception);
        }
    }

    private SemanticJobMatchResponse success(SemanticJobMatchResponse response) {
        counters.get("success").increment(); score.record(response.semanticScore()); return response;
    }

    private <R,E> List<SemanticMatchEvidence> convert(List<R> raws, List<E> entities,
            Function<E,Long> entityId, Function<E,String> title, Function<R,Long> rawId,
            ToDoubleFunction<R> rawScore, Function<R,List<EvidenceSource>> evidence, Function<R,String> reason,
            String category, int limit) {
        Map<Long,E> byId=entities.stream().collect(Collectors.toMap(entityId, Function.identity()));
        Map<Long,R> unique=new HashMap<>();
        for (R raw : safe(raws)) {
            Long id=rawId.apply(raw); if (!byId.containsKey(id)) throw new SemanticMatchException(SemanticMatchException.Reason.UNKNOWN_PKB_ID);
            validateScore(rawScore.applyAsDouble(raw));
            R prior=unique.get(id);
            if (prior != null) log.warn("Duplicate semantic match id={} category={}", id, category);
            if (prior == null || rawScore.applyAsDouble(raw) > rawScore.applyAsDouble(prior)) unique.put(id, raw);
        }
        return unique.values().stream().filter(raw -> rawScore.applyAsDouble(raw)>0)
                .map(raw -> new SemanticMatchEvidence(category, rawId.apply(raw), title.apply(byId.get(rawId.apply(raw))),
                        rawScore.applyAsDouble(raw), safe(evidence.apply(raw)), reason.apply(raw)))
                .sorted(ORDER).limit(limit).toList();
    }

    private void validateScore(double value) {
        if (!Double.isFinite(value) || value < 0 || value > 1) throw new SemanticMatchException(SemanticMatchException.Reason.SCORE_OUT_OF_RANGE);
    }
    private <T> List<T> safe(List<T> values) { return values == null ? List.of() : values; }
}
