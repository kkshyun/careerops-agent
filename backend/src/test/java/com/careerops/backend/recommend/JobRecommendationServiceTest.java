package com.careerops.backend.recommend;

import com.careerops.backend.job.*;
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
    JobPostingRepository jobs = mock(JobPostingRepository.class);
    RecommendationCandidateReader reader = mock(RecommendationCandidateReader.class);
    FakeClient client = new FakeClient();
    SimpleMeterRegistry registry = new SimpleMeterRegistry();
    JobRecommendationService service;

    @BeforeEach void setUp() { service = new JobRecommendationService(jobs, reader, client, registry); }

    @Test void emptyPkbIs409AndDoesNotCallClient() { given(input(List.of(candidate(1)), List.of())); assertThatThrownBy(() -> service.recommend(5)).isInstanceOf(ResponseStatusException.class); assertThat(client.calls).isZero(); }
    @Test void noOpenJobsReturnsEmptyWithoutClient() { given(input(List.of(), List.of(exp(11)))); assertThat(service.recommend(5).recommendations()).isEmpty(); assertThat(client.calls).isZero(); }
    @Test void normalRecommendationUsesDatabaseFieldsAndOneCall() { useJobs(job(1,"db-company","db-title")); client.results(result(raw(1,.7,List.of(11L)))); assertThat(service.recommend(5).recommendations().getFirst().title()).isEqualTo("db-title"); assertThat(client.calls).isOne(); }
    @Test void allOpenCandidatesIncludingBroadCategoryAreSentWithoutCap() { List<RecommendationJobCandidate> all=new ArrayList<>(); for(long i=1;i<=25;i++) all.add(candidate(i)); RecommendationJobCandidate broad=candidate(25,"정보통신"); all.set(24,broad); given(input(all,List.of(exp(11)))); client.results(result()); service.recommend(20); assertThat(client.inputs.getFirst().candidates()).hasSize(25).contains(broad); }
    @Test void unknownJobRetriesThenFailsAll() { useJobs(job(1,"c","t")); client.results(result(raw(2,.5,List.of())), result(raw(2,.5,List.of()))); assertReason(JobRecommendationException.Reason.UNKNOWN_JOB_ID,2); }
    @Test void duplicateKeepsHighestAndSortsDeterministically() { useJobs(job(1,"c","t"),job(2,"c","t"),job(3,"c","t")); client.results(result(raw(2,.2,List.of()),raw(3,.8,List.of()),raw(2,.9,List.of()),raw(1,.8,List.of()))); assertThat(service.recommend(3).recommendations()).extracting(JobRecommendation::jobId).containsExactly(2L,1L,3L); }
    @Test void appliesTopNAndReasonLimit() { useJobs(job(1,"c","t"),job(2,"c","t")); client.results(new RawRecommendationResult(List.of(new RawJobRecommendation(1L,.5,"x".repeat(201),List.of(),List.of(),List.of(),List.of()),raw(2,.4,List.of())))); assertThat(service.recommend(1).recommendations().getFirst().reason()).hasSize(200); }
    @Test void providerTopKFormula() { useJobs(job(1,"c","t")); client.results(result()); service.recommend(5); assertThat(client.topKs).containsExactly(20); client.reset(); service.recommend(20); assertThat(client.topKs).containsExactly(40); }
    @Test void providerMayExceedTopKAndServerTruncates() { List<JobPosting> values=new ArrayList<>(); List<RawJobRecommendation> raws=new ArrayList<>(); for(long i=1;i<=25;i++){values.add(job(i,"c","t"));raws.add(raw(i,i/100d,List.of()));} useJobs(values.toArray(JobPosting[]::new)); client.results(new RawRecommendationResult(raws)); assertThat(service.recommend(5).recommendations()).hasSize(5); }
    @Test void eachRepairableReasonRetriesAndRecovers() { for(var reason:List.of(JobRecommendationException.Reason.UNKNOWN_JOB_ID,JobRecommendationException.Reason.UNKNOWN_PKB_ID,JobRecommendationException.Reason.SCORE_OUT_OF_RANGE,JobRecommendationException.Reason.MALFORMED_RESPONSE)){ resetFlow(); client.failures(new JobRecommendationException(reason),null); client.results.add(result(raw(1,.5,List.of()))); assertThat(service.recommend(5).recommendations()).hasSize(1); assertThat(client.calls).isEqualTo(2); verify(reader).read(); } }
    @Test void malformedTwiceStopsAtTwo() { useJobs(job(1,"c","t")); client.failures(new JobRecommendationException(JobRecommendationException.Reason.MALFORMED_RESPONSE),new JobRecommendationException(JobRecommendationException.Reason.MALFORMED_RESPONSE)); assertReason(JobRecommendationException.Reason.MALFORMED_RESPONSE,2); }
    @Test void nonRepairableReasonsDoNotRetry() { for(var reason:List.of(JobRecommendationException.Reason.NETWORK_TIMEOUT,JobRecommendationException.Reason.PROVIDER_4XX,JobRecommendationException.Reason.PROVIDER_RETRY_EXHAUSTED)){ resetFlow(); client.failures(new JobRecommendationException(reason)); assertReason(reason,1); } }
    @Test void invalidScoresFromStructuredOutputFailWithoutClamp() { for(double score:List.of(-.1,1.1,Double.NaN)){ resetFlow(); RawRecommendationResult invalid=result(raw(1,score,List.of())); client.results(invalid,invalid); assertReason(JobRecommendationException.Reason.SCORE_OUT_OF_RANGE,2); } }
    @Test void everyUnknownPkbCategoryFromStructuredOutputFailsAll() {
        List<RawJobRecommendation> invalid=List.of(
                new RawJobRecommendation(1L,.5,"reason",List.of(999L),List.of(12L),List.of(13L),List.of(14L)),
                new RawJobRecommendation(1L,.5,"reason",List.of(11L),List.of(999L),List.of(13L),List.of(14L)),
                new RawJobRecommendation(1L,.5,"reason",List.of(11L),List.of(12L),List.of(999L),List.of(14L)),
                new RawJobRecommendation(1L,.5,"reason",List.of(11L),List.of(12L),List.of(13L),List.of(999L)));
        for(RawJobRecommendation raw:invalid){ resetFlow(); given(fullInput()); RawRecommendationResult result=result(raw); client.results(result,result); assertReason(JobRecommendationException.Reason.UNKNOWN_PKB_ID,2); }
    }
    @Test void nullStructuredOutputIsMalformed() { useJobs(job(1,"c","t")); client.results((RawRecommendationResult)null,null); assertReason(JobRecommendationException.Reason.MALFORMED_RESPONSE,2); }
    @Test void nullRecommendationsAreMalformed() { useJobs(job(1,"c","t")); RawRecommendationResult malformed=new RawRecommendationResult(null); client.results(malformed,malformed); assertReason(JobRecommendationException.Reason.MALFORMED_RESPONSE,2); }
    @Test void retryReusesSameInputAndReaderOnce() { useJobs(job(1,"c","t")); client.failures(new JobRecommendationException(JobRecommendationException.Reason.MALFORMED_RESPONSE),null); client.results.add(result(raw(1,.5,List.of()))); service.recommend(5); verify(reader).read(); assertThat(client.inputs.get(0)).isSameAs(client.inputs.get(1)); }
    @Test void retryMetricsCountAttemptsAndOutcome() { useJobs(job(1,"c","t")); client.failures(new JobRecommendationException(JobRecommendationException.Reason.MALFORMED_RESPONSE),new JobRecommendationException(JobRecommendationException.Reason.MALFORMED_RESPONSE)); catchThrowable(()->service.recommend(5)); assertThat(counter("careerops.recommendation.provider.retry","outcome","still_failed")).isEqualTo(1); assertThat(counter("careerops.recommendation.provider.validation_failure","reason","MALFORMED_RESPONSE")).isEqualTo(2); }
    @Test void normalFlowDoesNotIncrementRetryMetric() { useJobs(job(1,"c","t")); client.results(result()); service.recommend(5); assertThat(counter("careerops.recommendation.provider.retry","outcome","repaired")).isZero(); }
    @Test void repairSuccessMetricAndBaseMetricsAreOnce() { useJobs(job(1,"c","t")); client.failures(new JobRecommendationException(JobRecommendationException.Reason.MALFORMED_RESPONSE),null); client.results.add(result(raw(1,.5,List.of()))); service.recommend(5); assertThat(counter("careerops.recommendation.provider.retry","outcome","repaired")).isOne(); assertThat(registry.find("careerops.recommendation.candidates").summary().count()).isOne(); assertThat(registry.find("careerops.recommendation.returned").summary().count()).isOne(); assertThat(registry.find("careerops.recommendation.duration").timer().count()).isOne(); }

    private double counter(String name,String tag,String value){return registry.find(name).tag(tag,value).counter().count();}
    private void resetFlow(){ reset(reader,jobs); client.reset(); useJobs(job(1,"c","t")); }
    private void assertReason(JobRecommendationException.Reason reason,int calls){assertThatThrownBy(()->service.recommend(5)).isInstanceOfSatisfying(JobRecommendationException.class,e->assertThat(e.reason()).isEqualTo(reason));assertThat(client.calls).isEqualTo(calls);}
    private void useJobs(JobPosting... values){List<JobPosting> list=List.of(values);given(input(list.stream().map(v->candidate(v.getId())).toList(),List.of(exp(11))));when(jobs.findAllById(any())).thenAnswer(i->{Set<Long> ids=new HashSet<>();((Iterable<Long>)i.getArgument(0)).forEach(ids::add);return list.stream().filter(v->ids.contains(v.getId())).toList();});}
    private void given(RecommendationInput value){when(reader.read()).thenReturn(value);}
    private RecommendationInput input(List<RecommendationJobCandidate> c,List<RecommendationExperience> e){return new RecommendationInput(c,e,List.of(),List.of(),List.of());}
    private RecommendationJobCandidate candidate(long id){return new RecommendationJobCandidate(id,"c","t","IT","new","degree",LocalDate.now());}
    private RecommendationJobCandidate candidate(long id,String category){return new RecommendationJobCandidate(id,"c","t",category,"new","degree",LocalDate.now());}
    private RecommendationExperience exp(long id){return new RecommendationExperience(id,"e","o","r","s",List.of("tag"));}
    private RecommendationInput fullInput(){return new RecommendationInput(List.of(candidate(1)),List.of(exp(11)),List.of(new RecommendationCertification(12L,"cert","issuer")),List.of(new RecommendationEducation(13L,"school","major","degree","graduated")),List.of(new RecommendationAward(14L,"award","issuer")));}
    private JobPosting job(long id,String company,String title){JobPosting v=new JobPosting(company,title,"regular","new","degree","OPEN","i","IT","place",LocalDate.now(),LocalDate.now().plusDays(1),"MANUAL","url"+id,"e"+id);ReflectionTestUtils.setField(v,"id",id);return v;}
    private RawRecommendationResult result(RawJobRecommendation... values){return new RawRecommendationResult(List.of(values));}
    private RawJobRecommendation raw(long id,double score,List<Long> exps){return new RawJobRecommendation(id,score,"reason",exps,List.of(),List.of(),List.of());}

    static class FakeClient implements JobRecommendationClient {
        int calls; List<RecommendationInput> inputs=new ArrayList<>(); List<Integer> topKs=new ArrayList<>(); List<RuntimeException> failures=new ArrayList<>(); Queue<RawRecommendationResult> results=new LinkedList<>();
        void failures(RuntimeException... values){failures.addAll(Arrays.asList(values));} void results(RawRecommendationResult... values){results.addAll(Arrays.asList(values));} void reset(){calls=0;inputs.clear();topKs.clear();failures.clear();results.clear();}
        public RawRecommendationResult recommend(RecommendationInput input,int topK){inputs.add(input);topKs.add(topK);RuntimeException failure=calls<failures.size()?failures.get(calls):null;calls++;if(failure!=null)throw failure;return results.isEmpty()?new RawRecommendationResult(List.of()):results.remove();}
    }
}
