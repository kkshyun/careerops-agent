package com.careerops.backend.recommend;

import com.careerops.backend.career.*;
import com.careerops.backend.job.*;
import com.careerops.backend.pkbimport.*;
import com.careerops.backend.recommend.dto.*;
import io.micrometer.core.instrument.*;
import org.slf4j.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class JobRecommendationService {
    private static final Logger log=LoggerFactory.getLogger(JobRecommendationService.class);
    private static final Comparator<RawJobRecommendation> ORDER=Comparator.comparing(RawJobRecommendation::recommendationScore).reversed().thenComparing(RawJobRecommendation::jobId);
    private final JobPostingRepository jobs; private final CareerExperienceRepository experiences; private final ExperienceTagRepository tags;
    private final CertificationRepository certifications; private final EducationRepository educations; private final AwardRepository awards;
    private final ImportCandidateRepository importCandidates; private final JobRecommendationClient client;
    private final io.micrometer.core.instrument.Timer duration; private final DistributionSummary candidateMetric,returnedMetric; private final Map<String,Counter> counters=new HashMap<>();

    public JobRecommendationService(JobPostingRepository jobs,CareerExperienceRepository experiences,ExperienceTagRepository tags,
            CertificationRepository certifications,EducationRepository educations,AwardRepository awards,
            ImportCandidateRepository importCandidates,JobRecommendationClient client,MeterRegistry registry){
        this.jobs=jobs;this.experiences=experiences;this.tags=tags;this.certifications=certifications;this.educations=educations;this.awards=awards;this.importCandidates=importCandidates;this.client=client;
        duration=io.micrometer.core.instrument.Timer.builder("careerops.recommendation.duration").register(registry);
        candidateMetric=DistributionSummary.builder("careerops.recommendation.candidates").register(registry);
        returnedMetric=DistributionSummary.builder("careerops.recommendation.returned").register(registry);
        for(String result:List.of("success","pkb_empty","provider_error","validation_failed")) counters.put(result,Counter.builder("careerops.recommendation.request").tag("result",result).register(registry));
    }

    @Transactional(readOnly=true)
    public JobRecommendationResponse recommend(int limit){ return duration.record(()->calculate(limit)); }

    private JobRecommendationResponse calculate(int limit){
        long started=System.nanoTime();
        Set<Long> approved=importCandidates.findAll().stream().filter(v->v.getStatus()==ImportCandidateStatus.APPROVED).map(ImportCandidate::getId).collect(Collectors.toSet());
        List<CareerExperience> exps=experiences.findAll().stream().filter(v->approved(v.getSourceType(),v.getSourceImportCandidateId(),approved)).toList();
        List<Certification> certs=certifications.findAll().stream().filter(v->approved(v.getSourceType(),v.getSourceImportCandidateId(),approved)).toList();
        List<Education> edus=educations.findAll().stream().filter(v->approved(v.getSourceType(),v.getSourceImportCandidateId(),approved)).toList();
        List<Award> awardList=awards.findAll().stream().filter(v->approved(v.getSourceType(),v.getSourceImportCandidateId(),approved)).toList();
        if(exps.isEmpty()&&certs.isEmpty()&&edus.isEmpty()&&awardList.isEmpty()){ counters.get("pkb_empty").increment(); throw new ResponseStatusException(HttpStatus.CONFLICT,"승인된 PKB가 없어 공고를 추천할 수 없습니다"); }
        List<JobPosting> candidates=jobs.findAllByStatus("OPEN"); candidateMetric.record(candidates.size());
        if(candidates.isEmpty()){ counters.get("success").increment();returnedMetric.record(0);log.info("Job recommendation success candidates=0 returned=0 durationMs={} jobIds=[] scores=[]",elapsedMs(started));return new JobRecommendationResponse(List.of()); }
        try {
            List<Long> expIds=exps.stream().map(CareerExperience::getId).toList();
            Map<Long,List<ExperienceTag>> tagMap=(expIds.isEmpty()?List.<ExperienceTag>of():tags.findByCareerExperienceIdIn(expIds)).stream().collect(Collectors.groupingBy(v->v.getCareerExperience().getId()));
            RawRecommendationResult raw=client.recommend(candidates,exps,tagMap,certs,edus,awardList,limit);
            List<JobRecommendation> result=convert(raw,candidates,exps,certs,edus,awardList,limit);
            counters.get("success").increment();returnedMetric.record(result.size());
            log.info("Job recommendation success candidates={} returned={} durationMs={} jobIds={} scores={}",candidates.size(),result.size(),elapsedMs(started),result.stream().map(JobRecommendation::jobId).toList(),result.stream().map(JobRecommendation::recommendationScore).toList());
            return new JobRecommendationResponse(result);
        } catch(JobRecommendationException e){ counters.get(e.isValidationFailure()?"validation_failed":"provider_error").increment();log.warn("Job recommendation failed candidates={} durationMs={} reason={}",candidates.size(),elapsedMs(started),e.reason());throw e;
        } catch(RuntimeException e){ counters.get("provider_error").increment();log.warn("Job recommendation failed candidates={} durationMs={} reason=MALFORMED_RESPONSE",candidates.size(),elapsedMs(started));throw new JobRecommendationException(JobRecommendationException.Reason.MALFORMED_RESPONSE,e); }
    }

    private List<JobRecommendation> convert(RawRecommendationResult result,List<JobPosting> jobs,List<CareerExperience> exps,List<Certification> certs,List<Education> edus,List<Award> awards,int limit){
        if(result==null||result.recommendations()==null)throw new JobRecommendationException(JobRecommendationException.Reason.MALFORMED_RESPONSE);
        Map<Long,JobPosting> byJob=jobs.stream().collect(Collectors.toMap(JobPosting::getId,Function.identity()));
        Set<Long> expIds=ids(exps,CareerExperience::getId),certIds=ids(certs,Certification::getId),eduIds=ids(edus,Education::getId),awardIds=ids(awards,Award::getId);
        Map<Long,RawJobRecommendation> unique=new HashMap<>();
        for(RawJobRecommendation raw:result.recommendations()){
            if(raw==null||raw.jobId()==null||!byJob.containsKey(raw.jobId()))throw new JobRecommendationException(JobRecommendationException.Reason.UNKNOWN_JOB_ID);
            if(raw.recommendationScore()==null||!Double.isFinite(raw.recommendationScore())||raw.recommendationScore()<0||raw.recommendationScore()>1)throw new JobRecommendationException(JobRecommendationException.Reason.SCORE_OUT_OF_RANGE);
            validateIds(raw.careerExperienceIds(),expIds);validateIds(raw.certificationIds(),certIds);validateIds(raw.educationIds(),eduIds);validateIds(raw.awardIds(),awardIds);
            RawJobRecommendation prior=unique.get(raw.jobId());if(prior==null||raw.recommendationScore()>prior.recommendationScore())unique.put(raw.jobId(),raw);
        }
        Map<Long,JobPosting> refreshed=this.jobs.findAllById(unique.keySet()).stream().collect(Collectors.toMap(JobPosting::getId,Function.identity()));
        if(!refreshed.keySet().containsAll(unique.keySet()))throw new JobRecommendationException(JobRecommendationException.Reason.UNKNOWN_JOB_ID);
        return unique.values().stream().sorted(ORDER).limit(limit).map(raw->{ JobPosting job=refreshed.get(raw.jobId());return new JobRecommendation(job.getId(),job.getCompanyName(),job.getTitle(),job.getApplicationEndAt(),raw.recommendationScore(),truncate(raw.reason(),200),safe(raw.careerExperienceIds()),safe(raw.certificationIds()),safe(raw.educationIds()),safe(raw.awardIds())); }).toList();
    }
    private boolean approved(SourceType source,Long candidateId,Set<Long> approved){ return source==SourceType.MANUAL||(source==SourceType.IMPORT&&candidateId!=null&&approved.contains(candidateId)); }
    private <E> Set<Long> ids(List<E> values,Function<E,Long> id){ return values.stream().map(id).collect(Collectors.toSet()); }
    private void validateIds(List<Long> values,Set<Long> allowed){ if(values==null)throw new JobRecommendationException(JobRecommendationException.Reason.MALFORMED_RESPONSE);if(values.stream().anyMatch(v->v==null||!allowed.contains(v)))throw new JobRecommendationException(JobRecommendationException.Reason.UNKNOWN_PKB_ID); }
    private <T> List<T> safe(List<T> values){ return values==null?List.of():List.copyOf(values); }
    private String truncate(String value,int max){ String text=value==null?"":value;return text.length()<=max?text:text.substring(0,max); }
    private long elapsedMs(long started){ return (System.nanoTime()-started)/1_000_000; }
}
