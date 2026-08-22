package com.careerops.backend.match;

import com.careerops.backend.career.*;
import com.careerops.backend.job.*;
import com.careerops.backend.match.dto.EvidenceSource;
import com.careerops.backend.match.semantic.*;
import com.careerops.backend.match.semantic.dto.*;
import com.careerops.backend.pkbimport.*;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.MeterRegistry;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional @Rollback
@Import(SemanticJobMatchControllerTest.FakeConfiguration.class)
class SemanticJobMatchControllerTest {
    @Autowired MockMvc mvc; @Autowired JobPostingRepository jobs; @Autowired CareerExperienceRepository experiences;
    @Autowired CertificationRepository certifications; @Autowired EducationRepository educations; @Autowired AwardRepository awards;
    @Autowired SourceDocumentRepository documents; @Autowired ImportBatchRepository batches; @Autowired ImportCandidateRepository candidates;
    @Autowired FakeClient fake; @Autowired MeterRegistry meters; @Autowired ObjectMapper objectMapper;

    @BeforeEach void reset() {
        fake.result=emptyResult(0.5); fake.failure=null; fake.calls=0;
        fake.receivedExperiences=List.of(); fake.receivedCertifications=List.of();
        fake.receivedEducations=List.of(); fake.receivedAwards=List.of();
    }

    @Test void missingJobAndEmptyPkbDoNotCallProvider() throws Exception {
        mvc.perform(post("/api/jobs/{id}/semantic-match", Long.MAX_VALUE)).andExpect(status().isNotFound());
        JobPosting job=job("AI", "IT");
        mvc.perform(post("/api/jobs/{id}/semantic-match", job.getId())).andExpect(status().isOk())
                .andExpect(jsonPath("$.deterministicScore").value(0.0)).andExpect(jsonPath("$.semanticScore").value(0.0))
                .andExpect(jsonPath("$.experienceMatches").isEmpty()).andExpect(jsonPath("$.gaps").isEmpty());
        assertThat(fake.calls).isZero();
    }

    @Test void restoresServerTitleDeduplicatesSortsTruncatesAndExcludesZeroScores() throws Exception {
        JobPosting job=job("AI service", "java"); List<CareerExperience> saved=new ArrayList<>();
        for(int i=0;i<6;i++) saved.add(experiences.saveAndFlush(new CareerExperience(ExperienceType.PROJECT,"actual-"+i,null,null,null,null,"AI",null)));
        List<RawExperienceMatch> raw=new ArrayList<>();
        raw.add(new RawExperienceMatch(saved.get(0).getId(),.2,List.of(EvidenceSource.EXPERIENCE_SUMMARY),"low"));
        raw.add(new RawExperienceMatch(saved.get(0).getId(),.9,List.of(EvidenceSource.EXPERIENCE_SUMMARY),"high"));
        for(int i=1;i<6;i++) raw.add(new RawExperienceMatch(saved.get(i).getId(),.8-i*.1,List.of(EvidenceSource.EXPERIENCE_TITLE),"match"));
        fake.result=new RawSemanticMatchResult(.8,raw,List.of(),List.of(),List.of(),List.of("AI"));
        mvc.perform(post("/api/jobs/{id}/semantic-match",job.getId())).andExpect(status().isOk())
                .andExpect(jsonPath("$.semanticScore").value(.8)).andExpect(jsonPath("$.experienceMatches.length()").value(5))
                .andExpect(jsonPath("$.experienceMatches[0].id").value(saved.get(0).getId()))
                .andExpect(jsonPath("$.experienceMatches[0].title").value("actual-0"));
    }

    @Test void rejectsUnknownIdsAndOutOfRangeScoresWithoutFallback() throws Exception {
        JobPosting job=job("AI", "IT"); CareerExperience saved=experiences.saveAndFlush(new CareerExperience(ExperienceType.PROJECT,"actual",null,null,null,null,null,null));
        fake.result=new RawSemanticMatchResult(.5,List.of(new RawExperienceMatch(Long.MAX_VALUE,.8,List.of(),"invented")),List.of(),List.of(),List.of(),List.of());
        mvc.perform(post("/api/jobs/{id}/semantic-match",job.getId())).andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.matchMethod").doesNotExist());
        fake.result=new RawSemanticMatchResult(1.1,List.of(new RawExperienceMatch(saved.getId(),.8,List.of(),"match")),List.of(),List.of(),List.of(),List.of());
        mvc.perform(post("/api/jobs/{id}/semantic-match",job.getId())).andExpect(status().isBadGateway());
    }

    @Test void educationTitleFallsBackToInstitutionButPrefersMajor() throws Exception {
        JobPosting job=job("education", "education");
        Education withoutMajor=educations.saveAndFlush(new Education("Sehwa High School",null,null,null,null,null,null,null,null));
        Education withMajor=educations.saveAndFlush(new Education("CareerOps University","Computer Science",null,null,null,null,null,null,null));
        fake.result=new RawSemanticMatchResult(.7,List.of(),List.of(),List.of(
                new RawEducationMatch(withoutMajor.getId(),.9,List.of(EvidenceSource.EDUCATION_DESCRIPTION),"school match"),
                new RawEducationMatch(withMajor.getId(),.8,List.of(EvidenceSource.EDUCATION_MAJOR),"major match")),List.of(),List.of());

        mvc.perform(post("/api/jobs/{id}/semantic-match",job.getId())).andExpect(status().isOk())
                .andExpect(jsonPath("$.educationMatches[0].id").value(withoutMajor.getId()))
                .andExpect(jsonPath("$.educationMatches[0].title").value("Sehwa High School"))
                .andExpect(jsonPath("$.educationMatches[1].id").value(withMajor.getId()))
                .andExpect(jsonPath("$.educationMatches[1].title").value("Computer Science"));
    }

    @Test void handlesAllCategoriesZeroFilteringProviderFailuresAndMetricIsolation() throws Exception {
        JobPosting job=job("java", "java"); CareerExperience exp=experiences.saveAndFlush(new CareerExperience(ExperienceType.PROJECT,"java",null,null,null,null,null,null));
        Certification cert=certifications.saveAndFlush(new Certification("cert",null,null,null,null,null));
        Education edu=educations.saveAndFlush(new Education("school","major",null,null,null,null,null,null,null));
        Award award=awards.saveAndFlush(new Award("award",null,null,null));
        fake.result=new RawSemanticMatchResult(.6,List.of(new RawExperienceMatch(exp.getId(),.4,List.of(EvidenceSource.EXPERIENCE_TITLE),"match")),
                List.of(new RawCertificationMatch(cert.getId(),0,List.of(),"none")),List.of(new RawEducationMatch(edu.getId(),0,List.of(),"none")),
                List.of(new RawAwardMatch(award.getId(),0,List.of(),"none")),List.of());
        double matchBefore=count("careerops.match.request","success");
        long matchDurationBefore=meters.timer("careerops.match.duration").count();
        long matchScoreBefore=meters.summary("careerops.match.score").count();
        double semanticBefore=count("careerops.semantic-match.request","success");
        mvc.perform(post("/api/jobs/{id}/semantic-match",job.getId())).andExpect(status().isOk())
                .andExpect(jsonPath("$.certificationMatches").isEmpty()).andExpect(jsonPath("$.educationMatches").isEmpty())
                .andExpect(jsonPath("$.awardMatches").isEmpty()).andExpect(jsonPath("$.eligibility").doesNotExist());
        assertThat(count("careerops.match.request","success")).isEqualTo(matchBefore);
        assertThat(meters.timer("careerops.match.duration").count()).isEqualTo(matchDurationBefore);
        assertThat(meters.summary("careerops.match.score").count()).isEqualTo(matchScoreBefore);
        assertThat(count("careerops.semantic-match.request","success")).isGreaterThanOrEqualTo(semanticBefore+1);
        fake.failure=new SemanticMatchException(SemanticMatchException.Reason.NETWORK_TIMEOUT);
        mvc.perform(post("/api/jobs/{id}/semantic-match",job.getId())).andExpect(status().isBadGateway());
        fake.failure=new SemanticMatchException(SemanticMatchException.Reason.MALFORMED_RESPONSE);
        mvc.perform(post("/api/jobs/{id}/semantic-match",job.getId())).andExpect(status().isBadGateway());
    }

    @Test void pendingAndRejectedImportCandidatesNeverReachSemanticClient() throws Exception {
        JobPosting job=job("secret pending experience", "IT");
        String hash=String.format("%064d", System.nanoTime());
        SourceDocument document=documents.saveAndFlush(new SourceDocument("source",DocumentType.OTHER,hash,"sensitive source"));
        ImportBatch batch=batches.saveAndFlush(new ImportBatch(document,ImportBatchStatus.OPEN));
        ImportCandidate pending=candidates.saveAndFlush(new ImportCandidate(batch,CandidateTargetType.CAREER_EXPERIENCE,
                "{\"type\":\"PROJECT\",\"title\":\"secret pending experience\"}"));
        ImportCandidate rejected=candidates.saveAndFlush(new ImportCandidate(batch,CandidateTargetType.CAREER_EXPERIENCE,
                "{\"type\":\"PROJECT\",\"title\":\"secret rejected experience\"}"));
        mvc.perform(post("/api/career/imports/batches/{batchId}/candidates/{candidateId}/reject",batch.getId(),rejected.getId()))
                .andExpect(status().isOk());
        CareerExperience approvedManual=experiences.saveAndFlush(new CareerExperience(ExperienceType.PROJECT,
                "approved manual experience",null,null,null,null,"approved summary",null));
        mvc.perform(post("/api/jobs/{id}/semantic-match",job.getId())).andExpect(status().isOk())
                .andExpect(jsonPath("$.experienceMatches").isEmpty());
        assertThat(pending.getStatus()).isEqualTo(ImportCandidateStatus.PENDING);
        assertThat(fake.calls).isEqualTo(1);
        assertThat(fake.receivedExperiences).extracting(CareerExperience::getId).containsExactly(approvedManual.getId());
        assertThat(fake.receivedExperiences).extracting(CareerExperience::getTitle)
                .containsExactly("approved manual experience")
                .doesNotContain("secret pending experience", "secret rejected experience");
    }

    @Test void deterministicScoreMatchesDeterministicEndpointForNonZeroResult() throws Exception {
        JobPosting job=job("Java backend", "java");
        experiences.saveAndFlush(new CareerExperience(ExperienceType.PROJECT,"Java API",null,null,null,null,null,null));
        String semantic=mvc.perform(post("/api/jobs/{id}/semantic-match",job.getId())).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String deterministic=mvc.perform(get("/api/jobs/{id}/match",job.getId())).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode semanticJson=objectMapper.readTree(semantic);
        JsonNode deterministicJson=objectMapper.readTree(deterministic);
        assertThat(semanticJson.get("deterministicScore").doubleValue()).isGreaterThan(0.0)
                .isEqualTo(deterministicJson.get("overallScore").doubleValue());
    }

    private double count(String name,String result){ return meters.counter(name,"result",result).count(); }
    private JobPosting job(String title,String category){ String id=String.valueOf(System.nanoTime()); return jobs.saveAndFlush(new JobPosting("Company",title,"regular","new","degree","OPEN","I",category,"Seoul",LocalDate.now(),LocalDate.now().plusDays(1),"MANUAL","https://example.com/"+id,id)); }
    private static RawSemanticMatchResult emptyResult(double score){ return new RawSemanticMatchResult(score,List.of(),List.of(),List.of(),List.of(),List.of()); }

    @TestConfiguration static class FakeConfiguration {
        @Bean @Primary FakeClient fakeSemanticJobMatchClient(){ return new FakeClient(); }
    }
    static class FakeClient implements SemanticJobMatchClient {
        RawSemanticMatchResult result; SemanticMatchException failure; int calls;
        List<CareerExperience> receivedExperiences=List.of(); List<Certification> receivedCertifications=List.of();
        List<Education> receivedEducations=List.of(); List<Award> receivedAwards=List.of();
        public RawSemanticMatchResult match(JobPosting p,List<CareerExperience> e,Map<Long,List<ExperienceTag>> t,List<Certification> c,List<Education> d,List<Award> a){
            calls++; receivedExperiences=List.copyOf(e); receivedCertifications=List.copyOf(c);
            receivedEducations=List.copyOf(d); receivedAwards=List.copyOf(a);
            if(failure!=null) throw failure; return result;
        }
    }
}
