package com.careerops.backend.agent;

import com.careerops.backend.agent.dto.*;
import com.careerops.backend.agent.llm.*;
import com.careerops.backend.agent.llm.dto.*;
import com.careerops.backend.career.*;
import com.careerops.backend.job.*;
import com.careerops.backend.match.SemanticJobMatchService;
import com.careerops.backend.match.dto.*;
import com.careerops.backend.match.semantic.SemanticMatchException;
import io.micrometer.core.instrument.*;
import org.slf4j.*;
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
public class AgentAnalysisService {
    private static final Logger log=LoggerFactory.getLogger(AgentAnalysisService.class);
    private final JobPostingRepository jobs; private final CareerExperienceRepository experiences;
    private final ExperienceTagRepository tags; private final ExperienceBulletRepository bullets;
    private final CertificationRepository certifications; private final EducationRepository educations; private final AwardRepository awards;
    private final SemanticJobMatchService semanticService; private final AgentAnalysisClient client; private final Clock clock;
    private final io.micrometer.core.instrument.Timer duration; private final DistributionSummary recommended; private final Map<String,Counter> counters=new HashMap<>();

    @Autowired
    public AgentAnalysisService(JobPostingRepository jobs,CareerExperienceRepository experiences,ExperienceTagRepository tags,
            ExperienceBulletRepository bullets,CertificationRepository certifications,EducationRepository educations,AwardRepository awards,
            SemanticJobMatchService semanticService,AgentAnalysisClient client,MeterRegistry registry){
        this(jobs,experiences,tags,bullets,certifications,educations,awards,semanticService,client,registry,Clock.systemUTC()); }
    AgentAnalysisService(JobPostingRepository jobs,CareerExperienceRepository experiences,ExperienceTagRepository tags,
            ExperienceBulletRepository bullets,CertificationRepository certifications,EducationRepository educations,AwardRepository awards,
            SemanticJobMatchService semanticService,AgentAnalysisClient client,MeterRegistry registry,Clock clock){
        this.jobs=jobs; this.experiences=experiences; this.tags=tags; this.bullets=bullets; this.certifications=certifications;
        this.educations=educations; this.awards=awards; this.semanticService=semanticService; this.client=client; this.clock=clock;
        duration=io.micrometer.core.instrument.Timer.builder("careerops.agent-analysis.duration").register(registry);
        recommended=DistributionSummary.builder("careerops.agent-analysis.recommended_experiences").register(registry);
        for(String result:List.of("success","job_not_found","pkb_empty","provider_error","validation_failed"))
            counters.put(result,Counter.builder("careerops.agent-analysis.request").tag("result",result).register(registry));
    }

    @Transactional(readOnly=true)
    public AgentAnalysisResponse analyze(Long jobId){ return duration.record(()->calculate(jobId)); }
    private AgentAnalysisResponse calculate(Long jobId){
        JobPosting job=jobs.findById(jobId).orElse(null);
        if(job==null){ counters.get("job_not_found").increment(); throw new ResponseStatusException(HttpStatus.NOT_FOUND); }
        if(experiences.count()==0&&certifications.count()==0&&educations.count()==0&&awards.count()==0){
            counters.get("pkb_empty").increment(); throw new ResponseStatusException(HttpStatus.CONFLICT,"승인된 PKB가 없어 지원 전략을 분석할 수 없습니다"); }
        try {
            SemanticJobMatchResponse match;
            try { match=semanticService.match(jobId); }
            catch(SemanticMatchException ex){ throw new AgentAnalysisException(AgentAnalysisException.Reason.SEMANTIC_MATCH_FAILED,ex); }
            List<CareerExperience> exp=orderedFind(experiences,match.experienceMatches());
            List<Certification> cert=orderedFind(certifications,match.certificationMatches());
            List<Education> edu=orderedFind(educations,match.educationMatches());
            List<Award> award=orderedFind(awards,match.awardMatches());
            List<Long> expIds=exp.stream().map(CareerExperience::getId).toList();
            Map<Long,List<ExperienceTag>> tagMap=(expIds.isEmpty()?List.<ExperienceTag>of():tags.findByCareerExperienceIdIn(expIds)).stream()
                    .collect(Collectors.groupingBy(v->v.getCareerExperience().getId()));
            Map<Long,List<ExperienceBullet>> bulletMap=(expIds.isEmpty()?List.<ExperienceBullet>of():bullets.findByCareerExperienceIdIn(expIds)).stream()
                    .sorted(Comparator.comparing(ExperienceBullet::getSortOrder))
                    .collect(Collectors.groupingBy(v->v.getCareerExperience().getId(),LinkedHashMap::new,Collectors.toList()));
            RawAgentAnalysisResult raw=client.analyze(job,exp,tagMap,bulletMap,match.experienceMatches(),cert,
                    match.certificationMatches(),edu,match.educationMatches(),award,match.awardMatches(),match.gaps());
            AgentAnalysisResponse response=convert(jobId,raw,match,exp,cert,edu,award);
            counters.get("success").increment(); recommended.record(response.recommendedExperiences().size()); return response;
        } catch(AgentAnalysisException ex){ counters.get(ex.isValidationFailure()?"validation_failed":"provider_error").increment();
            log.warn("Agent analysis failed reason={}",ex.reason()); throw ex;
        } catch(RuntimeException ex){ counters.get("provider_error").increment(); throw new AgentAnalysisException(AgentAnalysisException.Reason.MALFORMED_RESPONSE,ex); }
    }

    private AgentAnalysisResponse convert(Long jobId,RawAgentAnalysisResult raw,SemanticJobMatchResponse match,
            List<CareerExperience> exp,List<Certification> cert,List<Education> edu,List<Award> award){
        if(raw==null) throw new AgentAnalysisException(AgentAnalysisException.Reason.MALFORMED_RESPONSE);
        List<ExperienceRecommendation> expOut=convertExperiences(raw.recommendedExperiences(),exp,match.experienceMatches());
        return new AgentAnalysisResponse(jobId,text(raw.roleSummary()),limited(raw.keyThemes(),5,UnaryOperator.identity()),
                limited(raw.knownRequirements(),5,UnaryOperator.identity()),text(raw.positioningHeadline()),text(raw.positioningSummary()),expOut,
                convertPkb(raw.recommendedCertifications(),cert,Certification::getId,Certification::getName,match.certificationMatches(),"CERTIFICATION"),
                convertPkb(raw.recommendedEducations(),edu,Education::getId,v->v.getMajor()==null||v.getMajor().isBlank()?v.getInstitution():v.getMajor(),match.educationMatches(),"EDUCATION"),
                convertPkb(raw.recommendedAwards(),award,Award::getId,Award::getTitle,match.awardMatches(),"AWARD"),
                text(raw.primaryMessage()),limited(raw.secondaryMessages(),3,UnaryOperator.identity()),limited(raw.avoidOrBeCareful(),3,UnaryOperator.identity()),
                safe(match.gaps()),Instant.now(clock));
    }
    private List<ExperienceRecommendation> convertExperiences(List<RawExperienceRecommendation> raws,List<CareerExperience> entities,List<SemanticMatchEvidence> context){
        Map<Long,CareerExperience> byId=entities.stream().collect(Collectors.toMap(CareerExperience::getId,Function.identity())); Map<Long,Double> scores=scores(context);
        List<RawExperienceRecommendation> clean=dedup(raws,byId.keySet(),"CAREER_EXPERIENCE").stream().limit(5).toList(); List<ExperienceRecommendation> out=new ArrayList<>();
        for(int i=0;i<clean.size();i++){ var raw=clean.get(i); out.add(new ExperienceRecommendation(raw.id(),byId.get(raw.id()).getTitle(),i+1,scores.get(raw.id()),
                    truncate(raw.reason(),300),limited(raw.emphasisPoints(),5,v->truncate(v,150)),safe(raw.evidence()))); } return List.copyOf(out);
    }
    private <E> List<PkbRecommendation> convertPkb(List<RawPkbRecommendation> raws,List<E> entities,Function<E,Long> id,Function<E,String> title,
            List<SemanticMatchEvidence> context,String category){ Map<Long,E> byId=entities.stream().collect(Collectors.toMap(id,Function.identity())); Map<Long,Double> scores=scores(context);
        return dedup(raws,byId.keySet(),category).stream().limit(3).map(raw->new PkbRecommendation(raw.id(),title.apply(byId.get(raw.id())),scores.get(raw.id()),truncate(raw.reason(),300),safe(raw.evidence()))).toList(); }
    private <R> List<R> dedup(List<R> raws,Set<Long> candidates,String category){ LinkedHashMap<Long,R> unique=new LinkedHashMap<>();
        for(R raw:safe(raws)){ Long id=raw instanceof RawExperienceRecommendation e?e.id():((RawPkbRecommendation)raw).id();
            if(!candidates.contains(id)) throw new AgentAnalysisException(AgentAnalysisException.Reason.UNKNOWN_CANDIDATE_ID);
            if(unique.putIfAbsent(id,raw)!=null) log.warn("Duplicate agent recommendation id={} category={}",id,category); } return List.copyOf(unique.values()); }
    private Map<Long,Double> scores(List<SemanticMatchEvidence> values){ return safe(values).stream().collect(Collectors.toMap(SemanticMatchEvidence::id,SemanticMatchEvidence::score,(a,b)->a)); }
    private <E> List<E> orderedFind(org.springframework.data.jpa.repository.JpaRepository<E,Long> repo,List<SemanticMatchEvidence> context){
        List<Long> ids=safe(context).stream().map(SemanticMatchEvidence::id).toList(); if(ids.isEmpty()) return List.of();
        Map<Long,E> found=new HashMap<>(); for(E value:repo.findAllById(ids)){ Long id=(value instanceof CareerExperience v)?v.getId():(value instanceof Certification v)?v.getId():(value instanceof Education v)?v.getId():((Award)value).getId(); found.put(id,value); }
        return ids.stream().map(found::get).filter(Objects::nonNull).toList(); }
    private <T> List<T> limited(List<T> values,int max,UnaryOperator<T> mapper){ return safe(values).stream().limit(max).map(mapper).toList(); }
    private String truncate(String value,int max){ String safe=text(value); return safe.length()<=max?safe:safe.substring(0,max); }
    private String text(String value){ return value==null?"":value; }
    private <T> List<T> safe(List<T> values){ return values==null?List.of():values; }
}
