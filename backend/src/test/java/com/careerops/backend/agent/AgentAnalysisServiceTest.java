package com.careerops.backend.agent;

import com.careerops.backend.agent.llm.AgentAnalysisClient;
import com.careerops.backend.agent.llm.dto.*;
import com.careerops.backend.career.*;
import com.careerops.backend.job.*;
import com.careerops.backend.match.SemanticJobMatchService;
import com.careerops.backend.match.dto.*;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import java.time.Instant;
import java.util.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AgentAnalysisServiceTest {
    @Test void categoryCapsKeepOriginalArrayOrderInsteadOfSortingBySemanticScore(){
        JobPostingRepository jobs=mock(JobPostingRepository.class); CareerExperienceRepository exps=mock(CareerExperienceRepository.class);
        ExperienceTagRepository tags=mock(ExperienceTagRepository.class); ExperienceBulletRepository bullets=mock(ExperienceBulletRepository.class);
        CertificationRepository certs=mock(CertificationRepository.class); EducationRepository edus=mock(EducationRepository.class); AwardRepository awards=mock(AwardRepository.class);
        SemanticJobMatchService semantic=mock(SemanticJobMatchService.class); AgentAnalysisClient client=mock(AgentAnalysisClient.class);
        JobPosting job=new JobPosting("c","j","regular","new","degree","OPEN","i","IT","s",null,null,"MANUAL","url","id"); ReflectionTestUtils.setField(job,"id",1L);
        List<CareerExperience> expValues=new ArrayList<>(); for(long i=1;i<=6;i++){ CareerExperience v=new CareerExperience(ExperienceType.PROJECT,"e"+i,null,null,null,null,null,null); ReflectionTestUtils.setField(v,"id",i); expValues.add(v); }
        List<Certification> certValues=new ArrayList<>(); List<Education> eduValues=new ArrayList<>(); List<Award> awardValues=new ArrayList<>();
        for(long i=1;i<=4;i++){ Certification c=new Certification("c"+i,null,null,null,null,null); ReflectionTestUtils.setField(c,"id",i); certValues.add(c);
            Education d=new Education("school","d"+i,null,null,null,null,null,null,null); ReflectionTestUtils.setField(d,"id",i); eduValues.add(d);
            Award a=new Award("a"+i,null,null,null); ReflectionTestUtils.setField(a,"id",i); awardValues.add(a); }
        List<SemanticMatchEvidence> em=context(6,"CAREER_EXPERIENCE"),cm=context(4,"CERTIFICATION"),dm=context(4,"EDUCATION"),am=context(4,"AWARD");
        when(jobs.findById(1L)).thenReturn(Optional.of(job)); when(exps.count()).thenReturn(1L); when(exps.findAllById(any())).thenReturn(expValues);
        when(certs.findAllById(any())).thenReturn(certValues); when(edus.findAllById(any())).thenReturn(eduValues); when(awards.findAllById(any())).thenReturn(awardValues);
        when(tags.findByCareerExperienceIdIn(anyList())).thenReturn(List.of()); when(bullets.findByCareerExperienceIdIn(anyList())).thenReturn(List.of());
        when(semantic.match(1L)).thenReturn(new SemanticJobMatchResponse(1L,0,.5,em,cm,dm,am,List.of(),Instant.EPOCH));
        List<RawExperienceRecommendation> er=expValues.stream().map(v->new RawExperienceRecommendation(v.getId(),"r",List.of(),List.of())).toList();
        List<RawPkbRecommendation> cr=certValues.stream().map(v->new RawPkbRecommendation(v.getId(),"r",List.of())).toList();
        List<RawPkbRecommendation> dr=eduValues.stream().map(v->new RawPkbRecommendation(v.getId(),"r",List.of())).toList();
        List<RawPkbRecommendation> ar=awardValues.stream().map(v->new RawPkbRecommendation(v.getId(),"r",List.of())).toList();
        when(client.analyze(any(),anyList(),anyMap(),anyMap(),anyList(),anyList(),anyList(),anyList(),anyList(),anyList(),anyList(),anyList()))
                .thenReturn(new RawAgentAnalysisResult("",List.of(),List.of(),"","",er,cr,dr,ar,"",List.of(),List.of()));
        AgentAnalysisService service=new AgentAnalysisService(jobs,exps,tags,bullets,certs,edus,awards,semantic,client,new SimpleMeterRegistry(),java.time.Clock.systemUTC());
        var result=service.analyze(1L);
        assertThat(result.recommendedExperiences()).extracting(v->v.id()).containsExactly(1L,2L,3L,4L,5L);
        assertThat(result.recommendedExperiences()).extracting(v->v.priority()).containsExactly(1,2,3,4,5);
        assertThat(result.recommendedCertifications()).extracting(v->v.id()).containsExactly(1L,2L,3L);
        assertThat(result.recommendedEducations()).extracting(v->v.id()).containsExactly(1L,2L,3L);
        assertThat(result.recommendedAwards()).extracting(v->v.id()).containsExactly(1L,2L,3L);
        assertThat(result.recommendedExperiences()).extracting(v->v.semanticMatchScore()).containsExactly(.1,.2,.3,.4,.5);
    }
    private static List<SemanticMatchEvidence> context(int count,String type){ List<SemanticMatchEvidence> out=new ArrayList<>(); for(long i=1;i<=count;i++) out.add(new SemanticMatchEvidence(type,i,"t"+i,i/10.0,List.of(),"ignored")); return out; }
}
