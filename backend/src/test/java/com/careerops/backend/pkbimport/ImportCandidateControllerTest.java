package com.careerops.backend.pkbimport;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.careerops.backend.career.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.annotation.Rollback;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.slf4j.LoggerFactory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@Rollback
class ImportCandidateControllerTest {
    @Autowired MockMvc mockMvc;
    @Autowired SourceDocumentRepository documentRepository;
    @Autowired ImportBatchRepository batchRepository;
    @Autowired ImportCandidateRepository candidateRepository;
    @Autowired CareerExperienceRepository experienceRepository;
    @Autowired CertificationRepository certificationRepository;
    @Autowired EducationRepository educationRepository;
    @Autowired AwardRepository awardRepository;

    @Test
    void createsAllTargetTypesAndSupportsGetListFilterAndBatchIsolation() throws Exception {
        ImportBatch batch = batch();
        for (var item : new String[][]{
                {"CAREER_EXPERIENCE", "{\"type\":\"PROJECT\",\"title\":\"Project\"}"},
                {"CERTIFICATION", "{\"name\":\"SQLD\"}"},
                {"EDUCATION", "{\"institution\":\"University\"}"},
                {"AWARD", "{\"title\":\"Prize\"}"}}) {
            mockMvc.perform(post(url(batch) ).contentType("application/json")
                            .content(request(item[0], item[1])))
                    .andExpect(status().isCreated()).andExpect(jsonPath("$.status").value("PENDING"))
                    .andExpect(jsonPath("$.createdEntityId").isEmpty());
        }
        ImportCandidate candidate = candidateRepository.search(batch.getId(), null,
                org.springframework.data.domain.PageRequest.of(0, 1)).getContent().getFirst();
        mockMvc.perform(get(url(batch)).param("status", "PENDING").param("size", "2"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(4))
                .andExpect(jsonPath("$.content.length()").value(2));
        mockMvc.perform(get(url(batch) + "/{id}", candidate.getId())).andExpect(status().isOk());
        ImportBatch other = batch();
        mockMvc.perform(get(url(other) + "/{id}", candidate.getId())).andExpect(status().isNotFound());
    }

    @Test
    void rejectsInvalidValidationAndMalformedJsonWithoutLeakingParserText() throws Exception {
        ImportBatch batch = batch();
        long before = candidateRepository.count();
        mockMvc.perform(post(url(batch)).contentType("application/json")
                        .content(request("CAREER_EXPERIENCE", "{\"type\":\"PROJECT\"}")))
                .andExpect(status().isBadRequest()).andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("through reference chain"))));
        mockMvc.perform(post(url(batch)).contentType("application/json")
                        .content(request("AWARD", "{secret malformed")))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("secret"))));
        assertThat(candidateRepository.count()).isEqualTo(before);
    }

    @Test
    void missingAndCompletedBatchRejectCreation() throws Exception {
        mockMvc.perform(post("/api/career/imports/batches/{id}/candidates", Long.MAX_VALUE)
                        .contentType("application/json").content(request("AWARD", "{\"title\":\"x\"}")))
                .andExpect(status().isNotFound());
        ImportBatch batch = batch();
        mockMvc.perform(post("/api/career/imports/batches/{id}/complete", batch.getId())).andExpect(status().isOk());
        mockMvc.perform(post(url(batch)).contentType("application/json")
                        .content(request("AWARD", "{\"title\":\"x\"}")))
                .andExpect(status().isConflict());
    }

    @Test
    void approveCreatesAllPkbTypesWithProvenance() throws Exception {
        ImportBatch batch = batch();
        approveAndAssert(batch, CandidateTargetType.CAREER_EXPERIENCE,
                "{\"type\":\"PROJECT\",\"title\":\"Project\"}");
        approveAndAssert(batch, CandidateTargetType.CERTIFICATION, "{\"name\":\"SQLD\"}");
        approveAndAssert(batch, CandidateTargetType.EDUCATION, "{\"institution\":\"University\"}");
        approveAndAssert(batch, CandidateTargetType.AWARD, "{\"title\":\"Prize\"}");
    }

    @Test
    void terminalTransitionsReturnConflictAndRejectCreatesNoPkbRow() throws Exception {
        ImportBatch batch = batch();
        ImportCandidate approved = candidateRepository.saveAndFlush(new ImportCandidate(batch,
                CandidateTargetType.AWARD, "{\"title\":\"approved\"}"));
        mockMvc.perform(post(url(batch) + "/{id}/approve", approved.getId())).andExpect(status().isOk());
        long awards = awardRepository.count();
        mockMvc.perform(post(url(batch) + "/{id}/approve", approved.getId())).andExpect(status().isConflict());
        mockMvc.perform(post(url(batch) + "/{id}/reject", approved.getId())).andExpect(status().isConflict());
        assertThat(awardRepository.count()).isEqualTo(awards);

        ImportCandidate rejected = candidateRepository.saveAndFlush(new ImportCandidate(batch,
                CandidateTargetType.AWARD, "{\"title\":\"rejected\"}"));
        mockMvc.perform(post(url(batch) + "/{id}/reject", rejected.getId()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("REJECTED"));
        mockMvc.perform(post(url(batch) + "/{id}/reject", rejected.getId())).andExpect(status().isConflict());
        mockMvc.perform(post(url(batch) + "/{id}/approve", rejected.getId())).andExpect(status().isConflict());
        assertThat(awardRepository.count()).isEqualTo(awards);
    }

    @Test
    void candidatePayloadIsNotWrittenToApplicationLogs() throws Exception {
        String sensitive = "CANDIDATE_PAYLOAD_MUST_NOT_APPEAR_91d23e";
        Logger root = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
        ListAppender<ILoggingEvent> appender = new ListAppender<>(); appender.start(); root.addAppender(appender);
        try {
            ImportBatch batch = batch();
            mockMvc.perform(post(url(batch)).contentType("application/json")
                            .content(request("AWARD", "{\"title\":\"" + sensitive + "\"}")))
                    .andExpect(status().isCreated());
            assertThat(appender.list).extracting(ILoggingEvent::getFormattedMessage)
                    .noneMatch(message -> message.contains(sensitive));
        } finally { root.detachAppender(appender); appender.stop(); }
    }

    private void approveAndAssert(ImportBatch batch, CandidateTargetType type, String payload) throws Exception {
        ImportCandidate candidate = candidateRepository.saveAndFlush(new ImportCandidate(batch, type, payload));
        mockMvc.perform(post(url(batch) + "/{id}/approve", candidate.getId()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("APPROVED"))
                .andExpect(jsonPath("$.createdEntityId").isNumber());
        switch (type) {
            case CAREER_EXPERIENCE -> assertProvenance(experienceRepository.findById(
                    candidateRepository.findById(candidate.getId()).orElseThrow().getCreatedEntityId()).orElseThrow(), candidate.getId());
            case CERTIFICATION -> assertProvenance(certificationRepository.findById(
                    candidateRepository.findById(candidate.getId()).orElseThrow().getCreatedEntityId()).orElseThrow(), candidate.getId());
            case EDUCATION -> assertProvenance(educationRepository.findById(
                    candidateRepository.findById(candidate.getId()).orElseThrow().getCreatedEntityId()).orElseThrow(), candidate.getId());
            case AWARD -> assertProvenance(awardRepository.findById(
                    candidateRepository.findById(candidate.getId()).orElseThrow().getCreatedEntityId()).orElseThrow(), candidate.getId());
        }
    }
    private void assertProvenance(Object entity, Long candidateId) {
        SourceType source; Long id;
        if (entity instanceof CareerExperience e) { source=e.getSourceType(); id=e.getSourceImportCandidateId(); }
        else if (entity instanceof Certification e) { source=e.getSourceType(); id=e.getSourceImportCandidateId(); }
        else if (entity instanceof Education e) { source=e.getSourceType(); id=e.getSourceImportCandidateId(); }
        else { Award e=(Award) entity; source=e.getSourceType(); id=e.getSourceImportCandidateId(); }
        assertThat(source).isEqualTo(SourceType.IMPORT); assertThat(id).isEqualTo(candidateId);
    }
    private ImportBatch batch() {
        SourceDocument doc = documentRepository.save(new SourceDocument("source", DocumentType.OTHER,
                "a".repeat(64), "raw"));
        return batchRepository.saveAndFlush(new ImportBatch(doc, ImportBatchStatus.OPEN));
    }
    private String url(ImportBatch batch) { return "/api/career/imports/batches/" + batch.getId() + "/candidates"; }
    private String request(String target, String payload) {
        return "{\"targetType\":\"" + target + "\",\"payload\":" + quote(payload) + "}";
    }
    private String quote(String value) { return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\""; }
}
