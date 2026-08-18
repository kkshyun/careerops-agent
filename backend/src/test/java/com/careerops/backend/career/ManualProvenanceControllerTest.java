package com.careerops.backend.career;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.annotation.Rollback;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@Rollback
class ManualProvenanceControllerTest {
    @Autowired MockMvc mockMvc;
    @Autowired CareerExperienceRepository experienceRepository;
    @Autowired CertificationRepository certificationRepository;
    @Autowired EducationRepository educationRepository;
    @Autowired AwardRepository awardRepository;

    @Test
    void directHttpCreatesAllPkbTypesAsManualWithoutCandidateReference() throws Exception {
        mockMvc.perform(post("/api/career/experiences").contentType("application/json")
                .content("{\"type\":\"PROJECT\",\"title\":\"manual\"}")).andExpect(status().isCreated());
        mockMvc.perform(post("/api/career/certifications").contentType("application/json")
                .content("{\"name\":\"manual\"}")).andExpect(status().isCreated());
        mockMvc.perform(post("/api/career/educations").contentType("application/json")
                .content("{\"institution\":\"manual\"}")).andExpect(status().isCreated());
        mockMvc.perform(post("/api/career/awards").contentType("application/json")
                .content("{\"title\":\"manual\"}")).andExpect(status().isCreated());
        assertManual(experienceRepository.findAll().getLast().getSourceType(),
                experienceRepository.findAll().getLast().getSourceImportCandidateId());
        assertManual(certificationRepository.findAll().getLast().getSourceType(),
                certificationRepository.findAll().getLast().getSourceImportCandidateId());
        assertManual(educationRepository.findAll().getLast().getSourceType(),
                educationRepository.findAll().getLast().getSourceImportCandidateId());
        assertManual(awardRepository.findAll().getLast().getSourceType(),
                awardRepository.findAll().getLast().getSourceImportCandidateId());
    }
    private void assertManual(SourceType sourceType, Long candidateId) {
        assertThat(sourceType).isEqualTo(SourceType.MANUAL); assertThat(candidateId).isNull();
    }
}
