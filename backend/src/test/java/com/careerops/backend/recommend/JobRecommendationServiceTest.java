package com.careerops.backend.recommend;

import com.careerops.backend.career.*;
import com.careerops.backend.job.*;
import com.careerops.backend.pkbimport.*;
import com.careerops.backend.recommend.dto.*;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.*;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;
import java.time.LocalDate;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class JobRecommendationServiceTest {
    JobPostingRepository jobs=mock(JobPostingRepository.class); CareerExperienceRepository experiences=mock(CareerExperienceRepository.class);
    ExperienceTagRepository tags=mock(ExperienceTagRepository.class); CertificationRepository certifications=mock(CertificationRepository.class);
    EducationRepository educations=mock(EducationRepository.class); AwardRepository awards=mock(AwardRepository.class);
    ImportCandidateRepository imports=mock(ImportCandidateRepository.class); FakeClient client=new FakeClient(); JobRecommendationService service;
    CareerExperience exp;

    @BeforeEach void setUp(){ exp=new CareerExperience(ExperienceType.PROJECT,"safe-title","org","role",null,null,"safe-summary","secret-detail"); ReflectionTestUtils.setField(exp,"id",11L);
        when(experiences.findAll()).thenReturn(List.of(exp));when(certifications.findAll()).thenReturn(List.of());when(educations.findAll()).thenReturn(List.of());when(awards.findAll()).thenReturn(List.of());when(imports.findAll()).thenReturn(List.of());when(tags.findByCareerExperienceIdIn(any())).thenReturn(List.of());
        service=new JobRecommendationService(jobs,experiences,tags,certifications,educations,awards,imports,client,new SimpleMeterRegistry()); }
    @Test void emptyPkbIs409AndDoesNotCallClient(){ when(experiences.findAll()).thenReturn(List.of());assertThatThrownBy(()->service.recommend(5)).isInstanceOfSatisfying(ResponseStatusException.class,e->assertThat(e.getStatusCode().value()).isEqualTo(409));assertThat(client.calls).isZero(); }
    @Test void noOpenJobsReturnsEmptyWithoutClient(){ when(jobs.findAllByStatus("OPEN")).thenReturn(List.of());assertThat(service.recommend(5).recommendations()).isEmpty();assertThat(client.calls).isZero(); }
    @Test void normalRecommendationUsesDatabaseFieldsAndOneCall(){ JobPosting a=job(1,"db-company","db-title","정보통신");useJobs(a);client.result=result(raw(1,.7,"reason",List.of(11L),List.of(),List.of(),List.of()));JobRecommendation out=service.recommend(5).recommendations().getFirst();assertThat(out.companyName()).isEqualTo("db-company");assertThat(out.title()).isEqualTo("db-title");assertThat(client.calls).isEqualTo(1); }
    @Test void allOpenCandidatesIncludingBroadCategoryAreSentWithoutCap(){ List<JobPosting> all=new ArrayList<>();for(int i=1;i<=25;i++)all.add(job((long)i,"c","t",i==25?"정보통신":"other"));when(jobs.findAllByStatus("OPEN")).thenReturn(all);client.result=result();service.recommend(20);verify(jobs).findAllByStatus("OPEN");assertThat(client.jobs).hasSize(25).anyMatch(v->"정보통신".equals(v.getJobCategory()));assertThat(client.calls).isEqualTo(1); }
    @Test void unknownJobFailsAll(){ useJobs(job(1,"c","t","IT"));client.result=result(raw(2,.5,"",List.of(),List.of(),List.of(),List.of()));assertReason(JobRecommendationException.Reason.UNKNOWN_JOB_ID); }
    @Test void duplicateKeepsHighestAndSortsDeterministically(){ useJobs(job(2,"c","t","IT"),job(1,"c","t","IT"),job(3,"c","t","IT"));client.result=result(raw(2,.2,"low",List.of(),List.of(),List.of(),List.of()),raw(3,.8,"",List.of(),List.of(),List.of(),List.of()),raw(2,.9,"high",List.of(),List.of(),List.of(),List.of()),raw(1,.8,"",List.of(),List.of(),List.of(),List.of()));List<JobRecommendation> out=service.recommend(3).recommendations();assertThat(out).extracting(JobRecommendation::jobId).containsExactly(2L,1L,3L);assertThat(out.getFirst().reason()).isEqualTo("high"); }
    @Test void appliesTopNAndReasonLimit(){ useJobs(job(1,"c","t","IT"),job(2,"c","t","IT"));client.result=result(raw(1,.5,"x".repeat(201),List.of(),List.of(),List.of(),List.of()),raw(2,.4,"",List.of(),List.of(),List.of(),List.of()));assertThat(service.recommend(1).recommendations()).singleElement().satisfies(v->assertThat(v.reason()).hasSize(200)); }
    @Test void invalidScoresFailWithoutClamp(){ useJobs(job(1,"c","t","IT"));for(double score:List.of(-.1,1.1,Double.NaN)){client.result=result(raw(1,score,"",List.of(),List.of(),List.of(),List.of()));assertReason(JobRecommendationException.Reason.SCORE_OUT_OF_RANGE);} }
    @Test void everyUnknownPkbCategoryFailsAll(){ useJobs(job(1,"c","t","IT"));List<RawJobRecommendation> invalid=List.of(raw(1,.5,"",List.of(99L),List.of(),List.of(),List.of()),raw(1,.5,"",List.of(),List.of(99L),List.of(),List.of()),raw(1,.5,"",List.of(),List.of(),List.of(99L),List.of()),raw(1,.5,"",List.of(),List.of(),List.of(),List.of(99L)));for(var raw:invalid){client.result=result(raw);assertReason(JobRecommendationException.Reason.UNKNOWN_PKB_ID);} }
    @Test void timeoutMalformedAndProviderFailuresBecome502Exception(){ useJobs(job(1,"c","t","IT"));for(var reason:List.of(JobRecommendationException.Reason.NETWORK_TIMEOUT,JobRecommendationException.Reason.MALFORMED_RESPONSE,JobRecommendationException.Reason.PROVIDER_RETRY_EXHAUSTED)){client.failure=new JobRecommendationException(reason);assertReason(reason);} }
    @Test void nullStructuredOutputIsMalformed(){ useJobs(job(1,"c","t","IT"));client.result=null;assertReason(JobRecommendationException.Reason.MALFORMED_RESPONSE); }
    @Test void onlyApprovedOrManualPkbIsSent(){ CareerExperience pending=importedExperience(12L,100L),rejected=importedExperience(13L,101L),approved=importedExperience(14L,102L);ImportCandidate pendingCandidate=candidate(100L,ImportCandidateStatus.PENDING),rejectedCandidate=candidate(101L,ImportCandidateStatus.REJECTED),approvedCandidate=candidate(102L,ImportCandidateStatus.APPROVED);when(imports.findAll()).thenReturn(List.of(pendingCandidate,rejectedCandidate,approvedCandidate));when(experiences.findAll()).thenReturn(List.of(exp,pending,rejected,approved));useJobs(job(1,"c","t","IT"));client.result=result();service.recommend(5);assertThat(client.exps).extracting(CareerExperience::getId).containsExactly(11L,14L); }
    private void assertReason(JobRecommendationException.Reason reason){assertThatThrownBy(()->service.recommend(5)).isInstanceOfSatisfying(JobRecommendationException.class,e->assertThat(e.reason()).isEqualTo(reason));client.failure=null;}
    private void useJobs(JobPosting... values){List<JobPosting> list=List.of(values);when(jobs.findAllByStatus("OPEN")).thenReturn(list);when(jobs.findAllById(any())).thenAnswer(invocation->{Iterable<Long> ids=invocation.getArgument(0);Set<Long> selected=new HashSet<>();ids.forEach(selected::add);return list.stream().filter(v->selected.contains(v.getId())).toList();});}
    private JobPosting job(long id,String company,String title,String category){JobPosting v=new JobPosting(company,title,"regular","new","degree","OPEN","i",category,"place",LocalDate.now(),LocalDate.now().plusDays(1),"MANUAL","url"+id,"e"+id);ReflectionTestUtils.setField(v,"id",id);return v;}
    private RawRecommendationResult result(RawJobRecommendation... values){return new RawRecommendationResult(List.of(values));}
    private RawJobRecommendation raw(long id,double score,String reason,List<Long> e,List<Long> c,List<Long> d,List<Long> a){return new RawJobRecommendation(id,score,reason,e,c,d,a);}
    private CareerExperience importedExperience(long id,long candidateId){CareerExperience value=mock(CareerExperience.class);when(value.getId()).thenReturn(id);when(value.getSourceType()).thenReturn(SourceType.IMPORT);when(value.getSourceImportCandidateId()).thenReturn(candidateId);return value;}
    private ImportCandidate candidate(long id,ImportCandidateStatus status){ImportCandidate value=mock(ImportCandidate.class);when(value.getId()).thenReturn(id);when(value.getStatus()).thenReturn(status);return value;}
    static class FakeClient implements JobRecommendationClient { int calls; RuntimeException failure; RawRecommendationResult result=new RawRecommendationResult(List.of());List<JobPosting> jobs;List<CareerExperience> exps;void result(RawRecommendationResult value){result=value;failure=null;}public RawRecommendationResult recommend(List<JobPosting> jobs,List<CareerExperience> exps,Map<Long,List<ExperienceTag>> tags,List<Certification> c,List<Education> d,List<Award> a,int limit){calls++;this.jobs=jobs;this.exps=exps;if(failure!=null)throw failure;return result;} }
}
