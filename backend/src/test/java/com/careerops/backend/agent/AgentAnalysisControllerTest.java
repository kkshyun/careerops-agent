package com.careerops.backend.agent;

import com.careerops.backend.agent.dto.AgentEvidenceSource;
import com.careerops.backend.agent.llm.*;
import com.careerops.backend.agent.llm.dto.*;
import com.careerops.backend.career.*;
import com.careerops.backend.job.*;
import com.careerops.backend.match.dto.EvidenceSource;
import com.careerops.backend.match.semantic.*;
import com.careerops.backend.match.semantic.dto.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.*;
import org.springframework.test.annotation.Rollback;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDate;
import java.util.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest @AutoConfigureMockMvc @Transactional @Rollback
@Import(AgentAnalysisControllerTest.FakeConfiguration.class)
class AgentAnalysisControllerTest {
    @Autowired MockMvc mvc; @Autowired JobPostingRepository jobs; @Autowired CareerExperienceRepository experiences;
    @Autowired CertificationRepository certifications; @Autowired EducationRepository educations; @Autowired AwardRepository awards;
    @Autowired ExperienceBulletRepository bullets; @Autowired FakeSemanticClient semantic; @Autowired FakeAgentClient agent;

    @BeforeEach void reset(){ semantic.calls=0; semantic.failure=null; semantic.result=semanticEmpty(); agent.calls=0; agent.failure=null; agent.result=agentEmpty(); }

    @Test void missingJobCallsNeitherLlm() throws Exception {
        mvc.perform(post("/api/jobs/{id}/agent-analysis",Long.MAX_VALUE)).andExpect(status().isNotFound());
        assertThat(semantic.calls).isZero(); assertThat(agent.calls).isZero();
    }
    @Test void emptyPkbReturnsConflictAndCallsNeitherLlm() throws Exception {
        JobPosting job=job();
        mvc.perform(post("/api/jobs/{id}/agent-analysis",job.getId())).andExpect(status().isConflict());
        assertThat(semantic.calls).isZero(); assertThat(agent.calls).isZero();
    }
    @Test void preservesAgentOrderAssignsPriorityAndSemanticScoresAndPassesGaps() throws Exception {
        JobPosting job=job(); CareerExperience first=experience("first"), second=experience("second"); Certification cert=certifications.saveAndFlush(new Certification("cert",null,null,null,null,null));
        semantic.result=new RawSemanticMatchResult(.7,List.of(match(first,.11),match(second,.91)),List.of(new RawCertificationMatch(cert.getId(),.37,List.of(),"ignored")),List.of(),List.of(),List.of("gap-from-match"));
        agent.result=new RawAgentAnalysisResult("role",List.of("a"),List.of("r"),"headline","summary",
                List.of(expRaw(second,"second reason"),expRaw(first,"first reason")),List.of(new RawPkbRecommendation(cert.getId(),"cert reason",List.of())),List.of(),List.of(),"primary",List.of(),List.of());
        mvc.perform(post("/api/jobs/{id}/agent-analysis",job.getId())).andExpect(status().isOk())
                .andExpect(jsonPath("$.recommendedExperiences[0].id").value(second.getId()))
                .andExpect(jsonPath("$.recommendedExperiences[0].priority").value(1)).andExpect(jsonPath("$.recommendedExperiences[0].semanticMatchScore").value(.91))
                .andExpect(jsonPath("$.recommendedExperiences[1].priority").value(2)).andExpect(jsonPath("$.recommendedExperiences[1].semanticMatchScore").value(.11))
                .andExpect(jsonPath("$.recommendedCertifications[0].semanticMatchScore").value(.37)).andExpect(jsonPath("$.gaps[0]").value("gap-from-match"));
    }
    @Test void unknownCandidateInEveryCategoryFailsWholeResponse() throws Exception {
        JobPosting job=job(); CareerExperience exp=experience("exp"); Certification cert=certifications.saveAndFlush(new Certification("cert",null,null,null,null,null));
        Education edu=educations.saveAndFlush(new Education("school","major",null,null,null,null,null,null,null)); Award award=awards.saveAndFlush(new Award("award",null,null,null));
        semantic.result=new RawSemanticMatchResult(.5,List.of(match(exp,.5)),List.of(new RawCertificationMatch(cert.getId(),.5,List.of(),"x")),
                List.of(new RawEducationMatch(edu.getId(),.5,List.of(),"x")),List.of(new RawAwardMatch(award.getId(),.5,List.of(),"x")),List.of());
        List<RawAgentAnalysisResult> invalid=List.of(
                raw(List.of(expRaw(Long.MAX_VALUE)),List.of(),List.of(),List.of()), raw(List.of(),List.of(pkb(Long.MAX_VALUE)),List.of(),List.of()),
                raw(List.of(),List.of(),List.of(pkb(Long.MAX_VALUE)),List.of()),raw(List.of(),List.of(),List.of(),List.of(pkb(Long.MAX_VALUE))));
        for(var value:invalid){ agent.result=value; mvc.perform(post("/api/jobs/{id}/agent-analysis",job.getId())).andExpect(status().isBadGateway()).andExpect(jsonPath("$.recommendedExperiences").doesNotExist()); }
    }
    @Test void duplicateKeepsFirstAndReassignsGaplessPriority() throws Exception {
        JobPosting job=job(); CareerExperience a=experience("a"),b=experience("b"); semantic.result=new RawSemanticMatchResult(.5,List.of(match(a,.5),match(b,.4)),List.of(),List.of(),List.of(),List.of());
        agent.result=raw(List.of(expRaw(a,"first"),expRaw(a,"discard"),expRaw(b,"second")),List.of(),List.of(),List.of());
        mvc.perform(post("/api/jobs/{id}/agent-analysis",job.getId())).andExpect(status().isOk())
                .andExpect(jsonPath("$.recommendedExperiences.length()").value(2)).andExpect(jsonPath("$.recommendedExperiences[0].reason").value("first"))
                .andExpect(jsonPath("$.recommendedExperiences[0].priority").value(1)).andExpect(jsonPath("$.recommendedExperiences[1].priority").value(2));
    }
    @Test void truncatesTextAndListLimitsWithoutFailure() throws Exception {
        JobPosting job=job(); CareerExperience exp=experience("exp"); semantic.result=new RawSemanticMatchResult(.5,List.of(match(exp,.5)),List.of(),List.of(),List.of(),List.of());
        agent.result=new RawAgentAnalysisResult("role",strings(6,"theme"),strings(6,"req"),"h","s",
                List.of(new RawExperienceRecommendation(exp.getId(),"x".repeat(301),strings(6,"y".repeat(151)),List.of())),List.of(),List.of(),List.of(),"p",strings(4,"secondary"),strings(4,"avoid"));
        mvc.perform(post("/api/jobs/{id}/agent-analysis",job.getId())).andExpect(status().isOk())
                .andExpect(jsonPath("$.keyThemes.length()").value(5)).andExpect(jsonPath("$.knownRequirements.length()").value(5))
                .andExpect(jsonPath("$.secondaryMessages.length()").value(3)).andExpect(jsonPath("$.avoidOrBeCareful.length()").value(3))
                .andExpect(jsonPath("$.recommendedExperiences[0].reason").value("x".repeat(300)))
                .andExpect(jsonPath("$.recommendedExperiences[0].emphasisPoints.length()").value(5))
                .andExpect(jsonPath("$.recommendedExperiences[0].emphasisPoints[0]").value("y".repeat(150)));
    }
    @Test void semanticFailureStopsAgentAndAgentFailuresReturnBadGateway() throws Exception {
        JobPosting job=job(); experience("exists"); semantic.failure=new SemanticMatchException(SemanticMatchException.Reason.NETWORK_TIMEOUT);
        mvc.perform(post("/api/jobs/{id}/agent-analysis",job.getId())).andExpect(status().isBadGateway()); assertThat(agent.calls).isZero();
        semantic.failure=null; agent.failure=new AgentAnalysisException(AgentAnalysisException.Reason.NETWORK_TIMEOUT);
        mvc.perform(post("/api/jobs/{id}/agent-analysis",job.getId())).andExpect(status().isBadGateway());
        agent.failure=new AgentAnalysisException(AgentAnalysisException.Reason.MALFORMED_RESPONSE);
        mvc.perform(post("/api/jobs/{id}/agent-analysis",job.getId())).andExpect(status().isBadGateway());
    }
    @Test void candidatePoolOnlyIsReloadedAndBulletsAreSorted() throws Exception {
        JobPosting job=job(); CareerExperience selected=experience("selected"),excluded=experience("excluded");
        bullets.saveAndFlush(new ExperienceBullet(selected,BulletType.RESULT,"later",2)); bullets.saveAndFlush(new ExperienceBullet(selected,BulletType.RESULT,"first",1));
        semantic.result=new RawSemanticMatchResult(.5,List.of(match(selected,.5)),List.of(),List.of(),List.of(),List.of());
        mvc.perform(post("/api/jobs/{id}/agent-analysis",job.getId())).andExpect(status().isOk());
        assertThat(agent.experiences).extracting(CareerExperience::getId).containsExactly(selected.getId()).doesNotContain(excluded.getId());
        assertThat(agent.bullets.get(selected.getId())).extracting(ExperienceBullet::getContent).containsExactly("first","later");
    }
    @Test void bulletRepositoryFindsAcrossMultipleExperienceIds(){
        CareerExperience a=experience("a"),b=experience("b"),outside=experience("outside");
        bullets.saveAndFlush(new ExperienceBullet(a,BulletType.RESULT,"a",1));
        bullets.saveAndFlush(new ExperienceBullet(b,BulletType.RESULT,"b",1));
        bullets.saveAndFlush(new ExperienceBullet(outside,BulletType.RESULT,"outside",1));
        assertThat(bullets.findByCareerExperienceIdIn(List.of(a.getId(),b.getId())))
                .extracting(ExperienceBullet::getContent).containsExactlyInAnyOrder("a","b");
    }

    private JobPosting job(){ String id=String.valueOf(System.nanoTime()); return jobs.saveAndFlush(new JobPosting("Company","AI","regular","new","degree","OPEN","I","IT","Seoul",LocalDate.now(),LocalDate.now().plusDays(1),"MANUAL","https://example.com/"+id,id)); }
    private CareerExperience experience(String title){ return experiences.saveAndFlush(new CareerExperience(ExperienceType.PROJECT,title,null,null,null,null,"summary",null)); }
    private static RawExperienceMatch match(CareerExperience e,double score){ return new RawExperienceMatch(e.getId(),score,List.of(EvidenceSource.EXPERIENCE_TITLE),"MATCH_REASON_MUST_NOT_PROPAGATE"); }
    private static RawExperienceRecommendation expRaw(CareerExperience e,String reason){ return expRaw(e.getId(),reason); }
    private static RawExperienceRecommendation expRaw(Long id){ return expRaw(id,"reason"); }
    private static RawExperienceRecommendation expRaw(Long id,String reason){ return new RawExperienceRecommendation(id,reason,List.of("point"),List.of(AgentEvidenceSource.EXPERIENCE_TITLE)); }
    private static RawPkbRecommendation pkb(Long id){ return new RawPkbRecommendation(id,"reason",List.of()); }
    private static List<String> strings(int n,String value){ return java.util.stream.IntStream.range(0,n).mapToObj(i->value).toList(); }
    private static RawAgentAnalysisResult raw(List<RawExperienceRecommendation> e,List<RawPkbRecommendation> c,List<RawPkbRecommendation> d,List<RawPkbRecommendation> a){ return new RawAgentAnalysisResult("",List.of(),List.of(),"","",e,c,d,a,"",List.of(),List.of()); }
    private static RawSemanticMatchResult semanticEmpty(){ return new RawSemanticMatchResult(.5,List.of(),List.of(),List.of(),List.of(),List.of()); }
    private static RawAgentAnalysisResult agentEmpty(){ return raw(List.of(),List.of(),List.of(),List.of()); }

    @TestConfiguration static class FakeConfiguration {
        @Bean @Primary FakeSemanticClient fakeSemanticJobMatchClient(){ return new FakeSemanticClient(); }
        @Bean @Primary FakeAgentClient fakeAgentAnalysisClient(){ return new FakeAgentClient(); }
    }
    static class FakeSemanticClient implements SemanticJobMatchClient {
        RawSemanticMatchResult result; SemanticMatchException failure; int calls;
        public RawSemanticMatchResult match(JobPosting p,List<CareerExperience> e,Map<Long,List<ExperienceTag>> t,List<Certification> c,List<Education> d,List<Award> a){ calls++; if(failure!=null) throw failure; return result; }
    }
    static class FakeAgentClient implements AgentAnalysisClient {
        RawAgentAnalysisResult result; AgentAnalysisException failure; int calls; List<CareerExperience> experiences=List.of(); Map<Long,List<ExperienceBullet>> bullets=Map.of();
        public RawAgentAnalysisResult analyze(JobPosting p,List<CareerExperience> e,Map<Long,List<ExperienceTag>> t,Map<Long,List<ExperienceBullet>> b,List<com.careerops.backend.match.dto.SemanticMatchEvidence> em,List<Certification> c,List<com.careerops.backend.match.dto.SemanticMatchEvidence> cm,List<Education> d,List<com.careerops.backend.match.dto.SemanticMatchEvidence> dm,List<Award> a,List<com.careerops.backend.match.dto.SemanticMatchEvidence> am,List<String> gaps){ calls++; experiences=List.copyOf(e); bullets=Map.copyOf(b); if(failure!=null) throw failure; return result; }
    }
}
