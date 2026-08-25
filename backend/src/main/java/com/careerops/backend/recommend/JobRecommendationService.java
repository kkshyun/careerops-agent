package com.careerops.backend.recommend;

import com.careerops.backend.job.*;
import com.careerops.backend.recommend.dto.*;
import io.micrometer.core.instrument.*;
import org.slf4j.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class JobRecommendationService {
    private static final Logger log = LoggerFactory.getLogger(JobRecommendationService.class);
    private static final Comparator<RawJobRecommendation> ORDER = Comparator
            .comparing(RawJobRecommendation::recommendationScore).reversed().thenComparing(RawJobRecommendation::jobId);
    private final JobPostingRepository jobs;
    private final RecommendationCandidateReader reader;
    private final JobRecommendationClient client;
    private final io.micrometer.core.instrument.Timer duration;
    private final DistributionSummary candidateMetric, returnedMetric;
    private final Map<String, Counter> counters = new HashMap<>(), retryCounters = new HashMap<>(), validationCounters = new HashMap<>();

    public JobRecommendationService(JobPostingRepository jobs, RecommendationCandidateReader reader,
            JobRecommendationClient client, MeterRegistry registry) {
        this.jobs = jobs; this.reader = reader; this.client = client;
        duration = io.micrometer.core.instrument.Timer.builder("careerops.recommendation.duration").register(registry);
        candidateMetric = DistributionSummary.builder("careerops.recommendation.candidates").register(registry);
        returnedMetric = DistributionSummary.builder("careerops.recommendation.returned").register(registry);
        for (String result : List.of("success", "pkb_empty", "provider_error", "validation_failed"))
            counters.put(result, Counter.builder("careerops.recommendation.request").tag("result", result).register(registry));
        for (String outcome : List.of("repaired", "still_failed"))
            retryCounters.put(outcome, Counter.builder("careerops.recommendation.provider.retry").tag("outcome", outcome).register(registry));
        for (JobRecommendationException.Reason reason : List.of(JobRecommendationException.Reason.UNKNOWN_JOB_ID,
                JobRecommendationException.Reason.UNKNOWN_PKB_ID, JobRecommendationException.Reason.SCORE_OUT_OF_RANGE,
                JobRecommendationException.Reason.MALFORMED_RESPONSE))
            validationCounters.put(reason.name(), Counter.builder("careerops.recommendation.provider.validation_failure")
                    .tag("reason", reason.name()).register(registry));
    }

    public JobRecommendationResponse recommend(int limit) { return duration.record(() -> calculate(limit)); }

    private JobRecommendationResponse calculate(int limit) {
        long started = System.nanoTime();
        RecommendationInput input = reader.read();
        candidateMetric.record(input.candidates().size());
        if (input.experiences().isEmpty() && input.certifications().isEmpty()
                && input.educations().isEmpty() && input.awards().isEmpty()) {
            counters.get("pkb_empty").increment();
            throw new ResponseStatusException(HttpStatus.CONFLICT, "승인된 PKB가 없어 공고를 추천할 수 없습니다");
        }
        if (input.candidates().isEmpty()) {
            counters.get("success").increment(); returnedMetric.record(0);
            log.info("Job recommendation success candidates=0 returned=0 durationMs={} jobIds=[] scores=[]", elapsedMs(started));
            return new JobRecommendationResponse(List.of());
        }
        int providerTopK = Math.max(limit * 2, 20);
        try {
            List<JobRecommendation> result;
            try {
                result = attempt(input, providerTopK, limit);
            } catch (JobRecommendationException first) {
                if (!first.isRepairable()) throw first;
                recordValidationFailure(first);
                try {
                    result = attempt(input, providerTopK, limit);
                    retryCounters.get("repaired").increment();
                } catch (JobRecommendationException second) {
                    if (second.isRepairable()) recordValidationFailure(second);
                    retryCounters.get("still_failed").increment();
                    throw second;
                }
            }
            counters.get("success").increment(); returnedMetric.record(result.size());
            log.info("Job recommendation success candidates={} returned={} durationMs={} jobIds={} scores={}",
                    input.candidates().size(), result.size(), elapsedMs(started),
                    result.stream().map(JobRecommendation::jobId).toList(),
                    result.stream().map(JobRecommendation::recommendationScore).toList());
            return new JobRecommendationResponse(result);
        } catch (JobRecommendationException e) {
            counters.get(e.isValidationFailure() ? "validation_failed" : "provider_error").increment();
            log.warn("Job recommendation failed candidates={} durationMs={} reason={} causeType={}",
                    input.candidates().size(), elapsedMs(started), e.reason(), causeType(e));
            throw e;
        }
    }

    private List<JobRecommendation> attempt(RecommendationInput input, int providerTopK, int limit) {
        try {
            return convert(client.recommend(input, providerTopK), input, limit);
        } catch (JobRecommendationException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new JobRecommendationException(JobRecommendationException.Reason.MALFORMED_RESPONSE, e);
        }
    }

    private List<JobRecommendation> convert(RawRecommendationResult result, RecommendationInput input, int limit) {
        if (result == null || result.recommendations() == null)
            throw new JobRecommendationException(JobRecommendationException.Reason.MALFORMED_RESPONSE);
        Set<Long> jobIds = ids(input.candidates(), RecommendationJobCandidate::id);
        Set<Long> expIds = ids(input.experiences(), RecommendationExperience::id);
        Set<Long> certIds = ids(input.certifications(), RecommendationCertification::id);
        Set<Long> eduIds = ids(input.educations(), RecommendationEducation::id);
        Set<Long> awardIds = ids(input.awards(), RecommendationAward::id);
        Map<Long, RawJobRecommendation> unique = new HashMap<>();
        for (RawJobRecommendation raw : result.recommendations()) {
            if (raw == null || raw.jobId() == null || !jobIds.contains(raw.jobId()))
                throw new JobRecommendationException(JobRecommendationException.Reason.UNKNOWN_JOB_ID);
            if (raw.recommendationScore() == null || !Double.isFinite(raw.recommendationScore())
                    || raw.recommendationScore() < 0 || raw.recommendationScore() > 1)
                throw new JobRecommendationException(JobRecommendationException.Reason.SCORE_OUT_OF_RANGE);
            validateIds(raw.careerExperienceIds(), expIds); validateIds(raw.certificationIds(), certIds);
            validateIds(raw.educationIds(), eduIds); validateIds(raw.awardIds(), awardIds);
            RawJobRecommendation prior = unique.get(raw.jobId());
            if (prior == null || raw.recommendationScore() > prior.recommendationScore()) unique.put(raw.jobId(), raw);
        }
        Map<Long, JobPosting> refreshed = jobs.findAllById(unique.keySet()).stream()
                .collect(Collectors.toMap(JobPosting::getId, Function.identity()));
        if (!refreshed.keySet().containsAll(unique.keySet()))
            throw new JobRecommendationException(JobRecommendationException.Reason.UNKNOWN_JOB_ID);
        return unique.values().stream().sorted(ORDER).limit(limit).map(raw -> {
            JobPosting job = refreshed.get(raw.jobId());
            return new JobRecommendation(job.getId(), job.getCompanyName(), job.getTitle(), job.getApplicationEndAt(),
                    raw.recommendationScore(), truncate(raw.reason(), 200), safe(raw.careerExperienceIds()),
                    safe(raw.certificationIds()), safe(raw.educationIds()), safe(raw.awardIds()));
        }).toList();
    }

    private void recordValidationFailure(JobRecommendationException e) { validationCounters.get(e.reason().name()).increment(); }
    private String causeType(JobRecommendationException e) { return e.getCause() == null ? "none" : e.getCause().getClass().getSimpleName(); }
    private <E> Set<Long> ids(List<E> values, Function<E, Long> id) { return values.stream().map(id).collect(Collectors.toSet()); }
    private void validateIds(List<Long> values, Set<Long> allowed) { if (values == null) throw new JobRecommendationException(JobRecommendationException.Reason.MALFORMED_RESPONSE); if (values.stream().anyMatch(v -> v == null || !allowed.contains(v))) throw new JobRecommendationException(JobRecommendationException.Reason.UNKNOWN_PKB_ID); }
    private <T> List<T> safe(List<T> values) { return values == null ? List.of() : List.copyOf(values); }
    private String truncate(String value, int max) { String text = value == null ? "" : value; return text.length() <= max ? text : text.substring(0, max); }
    private long elapsedMs(long started) { return (System.nanoTime() - started) / 1_000_000; }
}
