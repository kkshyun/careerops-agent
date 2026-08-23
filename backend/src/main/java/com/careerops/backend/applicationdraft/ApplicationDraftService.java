package com.careerops.backend.applicationdraft;

import com.careerops.backend.agent.AgentAnalysisService;
import com.careerops.backend.agent.dto.AgentAnalysisResponse;
import com.careerops.backend.agent.llm.AgentAnalysisException;
import com.careerops.backend.applicationdraft.dto.*;
import com.careerops.backend.applicationdraft.llm.*;
import com.careerops.backend.applicationdraft.llm.dto.*;
import com.careerops.backend.career.*;
import com.careerops.backend.job.*;
import io.micrometer.core.instrument.*;
import org.slf4j.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.time.*;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class ApplicationDraftService {
    private static final Logger log=LoggerFactory.getLogger(ApplicationDraftService.class);
    private final JobPostingRepository jobs; private final AgentAnalysisService analysis;
    private final CareerExperienceRepository experiences; private final ExperienceTagRepository tags;
    private final ExperienceBulletRepository bullets; private final CertificationRepository certifications;
    private final EducationRepository educations; private final AwardRepository awards; private final ApplicationDraftClient client;
    private final io.micrometer.core.instrument.Timer duration; private final DistributionSummary questionMetric,characterMetric; private final Map<String,Counter> requestCounters=new HashMap<>(),repairCounters=new HashMap<>();
    public ApplicationDraftService(JobPostingRepository jobs,AgentAnalysisService analysis,CareerExperienceRepository experiences,
            ExperienceTagRepository tags,ExperienceBulletRepository bullets,CertificationRepository certifications,
            EducationRepository educations,AwardRepository awards,ApplicationDraftClient client,MeterRegistry registry){
        this.jobs=jobs;this.analysis=analysis;this.experiences=experiences;this.tags=tags;this.bullets=bullets;this.certifications=certifications;this.educations=educations;this.awards=awards;this.client=client;
        duration=io.micrometer.core.instrument.Timer.builder("careerops.application-draft.duration").register(registry);questionMetric=DistributionSummary.builder("careerops.application-draft.questions").register(registry);characterMetric=DistributionSummary.builder("careerops.application-draft.characters").register(registry);
        for(String r:List.of("success","job_not_found","pkb_empty","invalid_request","provider_error","validation_failed"))requestCounters.put(r,Counter.builder("careerops.application-draft.request").tag("result",r).register(registry));
        for(String r:List.of("not_needed","success","failed"))repairCounters.put(r,Counter.builder("careerops.application-draft.repair").tag("result",r).register(registry));
    }
    @Transactional(readOnly=true)
    public ApplicationDraftResponse draft(Long jobId,ApplicationDraftRequest request){ return duration.record(()->calculate(jobId,request)); }
    private ApplicationDraftResponse calculate(Long jobId,ApplicationDraftRequest request){
        List<QuestionRequest> requested=request.questions(); questionMetric.record(requested.size());
        if(requested.stream().map(QuestionRequest::questionId).distinct().count()!=requested.size()){requestCounters.get("invalid_request").increment();throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"questionId must be unique");}
        JobPosting posting=jobs.findById(jobId).orElse(null); if(posting==null){requestCounters.get("job_not_found").increment();throw new ResponseStatusException(HttpStatus.NOT_FOUND);}
        try {
            AgentAnalysisResponse strategy;
            try{strategy=analysis.analyze(jobId);}catch(AgentAnalysisException ex){throw new ApplicationDraftException(ApplicationDraftException.Reason.AGENT_ANALYSIS_FAILED,ex);}
            List<CareerExperience> exp=experiences.findAll(); List<Certification> cert=certifications.findAll(); List<Education> edu=educations.findAll(); List<Award> award=awards.findAll();
            List<Long> expIds=exp.stream().map(CareerExperience::getId).toList();
            Map<Long,List<ExperienceTag>> tagMap=(expIds.isEmpty()?List.<ExperienceTag>of():tags.findByCareerExperienceIdIn(expIds)).stream().collect(Collectors.groupingBy(v->v.getCareerExperience().getId()));
            Map<Long,List<ExperienceBullet>> bulletMap=(expIds.isEmpty()?List.<ExperienceBullet>of():bullets.findByCareerExperienceIdIn(expIds)).stream().sorted(Comparator.comparing(ExperienceBullet::getSortOrder)).collect(Collectors.groupingBy(v->v.getCareerExperience().getId(),LinkedHashMap::new,Collectors.toList()));
            RawApplicationDraftResult raw=client.plan(posting,strategy,exp,tagMap,bulletMap,cert,edu,award,requested);
            LinkedHashMap<String,RawQuestionDraft> byQuestion=validateQuestions(raw,requested);
            validateAndDedupIds(byQuestion,ids(exp,CareerExperience::getId),ids(cert,Certification::getId),ids(edu,Education::getId),ids(award,Award::getId));
            applyRepair(requested,byQuestion);
            Map<Long,CareerExperience> expById=exp.stream().collect(Collectors.toMap(CareerExperience::getId,Function.identity()));
            List<QuestionDraftResult> output=new ArrayList<>(); for(QuestionRequest q:requested){RawQuestionDraft v=byQuestion.get(q.questionId());String text=text(v.draft());int count=text.length();boolean exceeded=q.maxLength()!=null&&count>q.maxLength();
                var result=new QuestionDraftResult(v.questionId(),v.primaryIntent(),safe(v.secondaryIntents()),v.primaryExperienceId(),safe(v.supportingExperienceIds()),safe(v.certificationIds()),safe(v.educationIds()),safe(v.awardIds()),text(v.coreMessage()),safe(v.outline()),text,count,q.maxLength(),exceeded,v.missingCompanyContext(),safe(v.warnings()));output.add(result);characterMetric.record(count);}
            RawOverallStrategy overall=raw.overallStrategy(); if(overall==null)throw new ApplicationDraftException(ApplicationDraftException.Reason.MALFORMED_RESPONSE);
            OverallStrategy overallOut=new OverallStrategy(text(overall.primaryPositioning()),distribution(output,expById),safe(overall.warnings()));
            requestCounters.get("success").increment();log.info("Application draft completed jobId={} questions={} drafts={} characters={}",jobId,requested.size(),output.size(),output.stream().mapToInt(QuestionDraftResult::characterCount).sum());
            return new ApplicationDraftResponse(jobId,List.copyOf(output),overallOut,Instant.now());
        }catch(ResponseStatusException ex){if(ex.getStatusCode()==HttpStatus.CONFLICT)requestCounters.get("pkb_empty").increment();throw ex;
        }catch(ApplicationDraftException ex){requestCounters.get(ex.isValidationFailure()?"validation_failed":"provider_error").increment();log.warn("Application draft failed jobId={} reason={}",jobId,ex.reason());throw ex;
        }catch(RuntimeException ex){requestCounters.get("provider_error").increment();throw new ApplicationDraftException(ApplicationDraftException.Reason.MALFORMED_RESPONSE,ex);}
    }
    private LinkedHashMap<String,RawQuestionDraft> validateQuestions(RawApplicationDraftResult raw,List<QuestionRequest> requested){
        if(raw==null||raw.questionResults()==null)throw new ApplicationDraftException(ApplicationDraftException.Reason.MALFORMED_RESPONSE);
        Set<String> expected=requested.stream().map(QuestionRequest::questionId).collect(Collectors.toSet());LinkedHashMap<String,RawQuestionDraft> result=new LinkedHashMap<>();
        for(var value:raw.questionResults()){if(value==null)throw new ApplicationDraftException(ApplicationDraftException.Reason.MALFORMED_RESPONSE);if(!expected.contains(value.questionId()))throw new ApplicationDraftException(ApplicationDraftException.Reason.UNKNOWN_QUESTION_ID);if(result.putIfAbsent(value.questionId(),value)!=null)throw new ApplicationDraftException(ApplicationDraftException.Reason.DUPLICATE_QUESTION_RESULT);}
        if(result.size()!=expected.size())throw new ApplicationDraftException(ApplicationDraftException.Reason.MISSING_QUESTION_RESULT);return result;
    }
    private void validateAndDedupIds(LinkedHashMap<String,RawQuestionDraft> values,Set<Long> exp,Set<Long> cert,Set<Long> edu,Set<Long> award){
        for(var entry:new ArrayList<>(values.entrySet())){var v=entry.getValue();known(v.primaryExperienceId(),exp);List<Long> supporting=dedup(v.supportingExperienceIds(),exp,v.questionId(),"CAREER_EXPERIENCE");List<Long> c=dedup(v.certificationIds(),cert,v.questionId(),"CERTIFICATION");List<Long> e=dedup(v.educationIds(),edu,v.questionId(),"EDUCATION");List<Long> a=dedup(v.awardIds(),award,v.questionId(),"AWARD");
            values.put(entry.getKey(),new RawQuestionDraft(v.questionId(),v.primaryIntent(),safe(v.secondaryIntents()),v.primaryExperienceId(),supporting,c,e,a,v.coreMessage(),safe(v.outline()),v.draft(),safe(v.warnings()),v.missingCompanyContext()));}
    }
    private <T> Set<Long> ids(List<T> values,Function<T,Long> mapper){return values.stream().map(mapper).collect(Collectors.toSet());}
    private void known(Long id,Set<Long> valid){if(id!=null&&!valid.contains(id))throw new ApplicationDraftException(ApplicationDraftException.Reason.UNKNOWN_CANDIDATE_ID);}
    private List<Long> dedup(List<Long> values,Set<Long> valid,String questionId,String category){LinkedHashSet<Long> unique=new LinkedHashSet<>();for(Long id:safe(values)){known(id,valid);if(!unique.add(id))log.warn("Duplicate application draft id={} category={} questionId={}",id,category,questionId);}return List.copyOf(unique);}
    private void applyRepair(List<QuestionRequest> requests,LinkedHashMap<String,RawQuestionDraft> drafts){List<QuestionRequest> over=requests.stream().filter(q->q.maxLength()!=null&&text(drafts.get(q.questionId()).draft()).length()>q.maxLength()).toList();if(over.isEmpty()){repairCounters.get("not_needed").increment();return;}
        List<RawQuestionDraft> originals=over.stream().map(q->drafts.get(q.questionId())).toList();try{var repaired=client.repair(over,originals);Map<String,String> replacements=validRepair(repaired,over);for(var entry:replacements.entrySet()){var v=drafts.get(entry.getKey());drafts.put(entry.getKey(),new RawQuestionDraft(v.questionId(),v.primaryIntent(),safe(v.secondaryIntents()),v.primaryExperienceId(),safe(v.supportingExperienceIds()),safe(v.certificationIds()),safe(v.educationIds()),safe(v.awardIds()),v.coreMessage(),safe(v.outline()),entry.getValue(),safe(v.warnings()),v.missingCompanyContext()));}repairCounters.get("success").increment();
        }catch(RuntimeException ex){repairCounters.get("failed").increment();log.warn("Application draft repair failed questions={}",over.size());}}
    private Map<String,String> validRepair(RawApplicationDraftRepairResult raw,List<QuestionRequest> requested){if(raw==null||raw.results()==null)throw new IllegalArgumentException();Set<String> expected=requested.stream().map(QuestionRequest::questionId).collect(Collectors.toSet());Map<String,String> out=new HashMap<>();for(var v:raw.results()){if(v==null||!expected.contains(v.questionId())||out.putIfAbsent(v.questionId(),text(v.draft()))!=null)throw new IllegalArgumentException();}if(out.size()!=expected.size())throw new IllegalArgumentException();return out;}
    private List<ExperienceDistributionEntry> distribution(List<QuestionDraftResult> values,Map<Long,CareerExperience> entities){LinkedHashMap<Long,LinkedHashSet<String>> used=new LinkedHashMap<>();for(var q:values){if(q.primaryExperienceId()!=null)used.computeIfAbsent(q.primaryExperienceId(),k->new LinkedHashSet<>()).add(q.questionId());for(Long id:q.supportingExperienceIds())used.computeIfAbsent(id,k->new LinkedHashSet<>()).add(q.questionId());}return used.entrySet().stream().map(e->new ExperienceDistributionEntry(e.getKey(),entities.get(e.getKey()).getTitle(),List.copyOf(e.getValue()))).toList();}
    private String text(String value){return value==null?"":value;} private <T> List<T> safe(List<T> value){return value==null?List.of():value;}
}
