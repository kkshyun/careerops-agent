package com.careerops.backend.match;

import com.careerops.backend.career.*;
import com.careerops.backend.job.JobPosting;
import com.careerops.backend.job.JobPostingRepository;
import com.careerops.backend.pkbimport.*;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.annotation.Rollback;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@Rollback
class JobMatchControllerTest {
    @Autowired MockMvc mockMvc;
    @Autowired JobPostingRepository jobRepository;
    @Autowired CareerExperienceRepository experienceRepository;
    @Autowired ExperienceTagRepository tagRepository;
    @Autowired CertificationRepository certificationRepository;
    @Autowired EducationRepository educationRepository;
    @Autowired AwardRepository awardRepository;
    @Autowired SourceDocumentRepository documentRepository;
    @Autowired ImportBatchRepository batchRepository;
    @Autowired ImportCandidateRepository candidateRepository;
    @Autowired MeterRegistry meterRegistry;

    @Test
    void returns404AndNotFoundMetricForMissingPosting() throws Exception {
        double before = meterRegistry.counter("careerops.match.request", "result", "not_found").count();
        mockMvc.perform(get("/api/jobs/{id}/match", Long.MAX_VALUE)).andExpect(status().isNotFound());
        assertThat(meterRegistry.counter("careerops.match.request", "result", "not_found").count())
                .isGreaterThanOrEqualTo(before + 1);
    }

    @Test
    void emptyPkbReturnsZeroEmptyEvidenceAllGapsAndEchoFields() throws Exception {
        JobPosting job = job("Java.Backend, Data , Cloud");
        mockMvc.perform(get("/api/jobs/{id}/match", job.getId()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.jobPostingId").value(job.getId()))
                .andExpect(jsonPath("$.overallScore").value(0.0))
                .andExpect(jsonPath("$.recommendedExperiences").isEmpty())
                .andExpect(jsonPath("$.recommendedCertifications").isEmpty())
                .andExpect(jsonPath("$.recommendedEducations").isEmpty())
                .andExpect(jsonPath("$.recommendedAwards").isEmpty())
                .andExpect(jsonPath("$.unmatchedJobCategories[0]").value("Java.Backend"))
                .andExpect(jsonPath("$.unmatchedJobCategories[1]").value("Data"))
                .andExpect(jsonPath("$.unmatchedJobCategories[2]").value("Cloud"))
                .andExpect(jsonPath("$.careerLevel").value("신입"))
                .andExpect(jsonPath("$.educationRequirement").value("대졸"))
                .andExpect(jsonPath("$.computedAt").isNotEmpty());
    }

    @Test
    void returnsExactEvidenceScoresGapsMetricsAndDeterministicContent() throws Exception {
        JobPosting job = job("java, cloud, security");
        CareerExperience first = experienceRepository.save(new CareerExperience(ExperienceType.PROJECT,
                "Java API", null, null, null, null, "cloud migration", "unrelated"));
        CareerExperience second = experienceRepository.save(new CareerExperience(ExperienceType.PROJECT,
                "Java batch", null, null, null, null, null, null));
        tagRepository.save(new ExperienceTag(first, "JAVA"));
        certificationRepository.save(new Certification("Cloud certificate", null, null, null, null, null));
        educationRepository.save(new Education("University", "Cloud Engineering", null, null,
                null, null, null, null, null));
        awardRepository.save(new Award("Cloud Award", null, null, null));
        awardRepository.save(new Award("Design Award", null, null, "visual design"));

        String response1 = mockMvc.perform(get("/api/jobs/{id}/match", job.getId()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.overallScore").value(1.0))
                .andExpect(jsonPath("$.recommendedExperiences[0].type").value("CAREER_EXPERIENCE"))
                .andExpect(jsonPath("$.recommendedExperiences[0].id").value(first.getId()))
                .andExpect(jsonPath("$.recommendedExperiences[0].score").value(1.0))
                .andExpect(jsonPath("$.recommendedExperiences[0].matchedFields[0]").value("tags"))
                .andExpect(jsonPath("$.recommendedExperiences[0].matchedFields[1]").value("title"))
                .andExpect(jsonPath("$.recommendedExperiences[0].matchedFields[2]").value("summary"))
                .andExpect(jsonPath("$.recommendedExperiences[1].id").value(second.getId()))
                .andExpect(jsonPath("$.recommendedCertifications[0].score").value(1.0))
                .andExpect(jsonPath("$.recommendedEducations[0].matchedFields[0]").value("major"))
                .andExpect(jsonPath("$.recommendedAwards.length()").value(1))
                .andExpect(jsonPath("$.unmatchedJobCategories[0]").value("security"))
                .andReturn().getResponse().getContentAsString();
        String response2 = mockMvc.perform(get("/api/jobs/{id}/match", job.getId()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(withoutComputedAt(response2)).isEqualTo(withoutComputedAt(response1));
        assertThat(meterRegistry.find("careerops.match.duration").timer()).isNotNull();
        assertThat(meterRegistry.find("careerops.match.score").summary()).isNotNull();
    }

    @Test
    void appliesTopLimitsAndIdAscendingTieOrdering() throws Exception {
        JobPosting job = job("java");
        List<Long> experienceIds = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            CareerExperience saved = experienceRepository.save(new CareerExperience(ExperienceType.PROJECT,
                    "java " + i, null, null, null, null, null, null));
            experienceIds.add(saved.getId());
        }
        for (int i = 0; i < 4; i++) {
            certificationRepository.save(new Certification("java cert " + i, null, null, null, null, null));
            educationRepository.save(new Education("School " + i, "java major " + i, null, null,
                    null, null, null, null, null));
            awardRepository.save(new Award("java award " + i, null, null, null));
        }
        mockMvc.perform(get("/api/jobs/{id}/match", job.getId())).andExpect(status().isOk())
                .andExpect(jsonPath("$.recommendedExperiences.length()").value(5))
                .andExpect(jsonPath("$.recommendedCertifications.length()").value(3))
                .andExpect(jsonPath("$.recommendedEducations.length()").value(3))
                .andExpect(jsonPath("$.recommendedAwards.length()").value(3))
                .andExpect(jsonPath("$.recommendedExperiences[*].id").value(contains(
                        experienceIds.get(0).intValue(), experienceIds.get(1).intValue(),
                        experienceIds.get(2).intValue(), experienceIds.get(3).intValue(),
                        experienceIds.get(4).intValue())))
                .andExpect(jsonPath("$.recommendedExperiences[*].id")
                        .value(not(hasItem(experienceIds.get(5).intValue()))));
    }

    @Test
    void pendingAndRejectedCandidatesDoNotMatchButApprovedCreatedEntityDoes() throws Exception {
        JobPosting job = job("java");
        ImportBatch batch = batch();
        ImportCandidate pending = candidateRepository.saveAndFlush(new ImportCandidate(batch,
                CandidateTargetType.CAREER_EXPERIENCE, "{\"type\":\"PROJECT\",\"title\":\"java pending\"}"));
        ImportCandidate rejected = candidateRepository.saveAndFlush(new ImportCandidate(batch,
                CandidateTargetType.CAREER_EXPERIENCE, "{\"type\":\"PROJECT\",\"title\":\"java rejected\"}"));
        mockMvc.perform(post(candidateUrl(batch, rejected) + "/reject")).andExpect(status().isOk());
        mockMvc.perform(get("/api/jobs/{id}/match", job.getId())).andExpect(status().isOk())
                .andExpect(jsonPath("$.recommendedExperiences").isEmpty());
        mockMvc.perform(post(candidateUrl(batch, pending) + "/approve")).andExpect(status().isOk());
        mockMvc.perform(get("/api/jobs/{id}/match", job.getId())).andExpect(status().isOk())
                .andExpect(jsonPath("$.recommendedExperiences.length()").value(1))
                .andExpect(jsonPath("$.recommendedExperiences[0].title").value("java pending"));
    }

    private JobPosting job(String category) {
        String unique = String.valueOf(System.nanoTime());
        return jobRepository.saveAndFlush(new JobPosting("Company", "Developer", "정규직", "신입", "대졸",
                "OPEN", "I001", category, "서울", LocalDate.now(), LocalDate.now().plusDays(10),
                "MANUAL", "https://example.com/" + unique, unique));
    }

    private ImportBatch batch() {
        String hash = String.format("%064d", System.nanoTime());
        SourceDocument document = documentRepository.save(new SourceDocument("source", DocumentType.OTHER, hash, "raw"));
        return batchRepository.saveAndFlush(new ImportBatch(document, ImportBatchStatus.OPEN));
    }

    private String candidateUrl(ImportBatch batch, ImportCandidate candidate) {
        return "/api/career/imports/batches/" + batch.getId() + "/candidates/" + candidate.getId();
    }

    private String withoutComputedAt(String json) {
        return json.replaceAll("\\\"computedAt\\\":\\\"[^\\\"]+\\\"", "\\\"computedAt\\\":\\\"ignored\\\"");
    }
}
