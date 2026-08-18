package com.careerops.backend.pkbimport;

import com.careerops.backend.career.*;
import com.careerops.backend.career.dto.*;
import com.careerops.backend.pkbimport.dto.ExtractionResponse;
import com.careerops.backend.pkbimport.extraction.llm.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
class ImportBatchExtractionServiceTest {
    @Autowired ImportBatchExtractionService service;
    @Autowired FakeClient client;
    @Autowired SourceDocumentRepository documentRepository;
    @Autowired ImportBatchRepository batchRepository;
    @Autowired ImportBatchService batchService;
    @Autowired ImportCandidateRepository candidateRepository;
    @Autowired CareerExperienceRepository careerRepository;
    @Autowired CertificationRepository certificationRepository;
    @Autowired EducationRepository educationRepository;
    @Autowired AwardRepository awardRepository;

    @BeforeEach void resetFake() { client.result = validResult(); client.failure = null; }

    @AfterEach void clean() {
        candidateRepository.deleteAll();
        batchRepository.deleteAll();
        documentRepository.deleteAll();
    }

    @Test
    void createsAllFourPendingCandidateTypesWithoutCreatingPkbRows() {
        long careers = careerRepository.count(), certifications = certificationRepository.count();
        long educations = educationRepository.count(), awards = awardRepository.count();
        ImportBatch batch = openBatch("private raw text");

        ExtractionResponse response = service.extract(batch.getId());

        assertThat(response.createdCandidateCount()).isEqualTo(4);
        assertThat(response.candidateIds()).hasSize(4).doesNotHaveDuplicates();
        assertThat(response.counts()).containsEntry(CandidateTargetType.CAREER_EXPERIENCE, 1)
                .containsEntry(CandidateTargetType.CERTIFICATION, 1)
                .containsEntry(CandidateTargetType.EDUCATION, 1)
                .containsEntry(CandidateTargetType.AWARD, 1);
        List<ImportCandidate> created = candidates(batch.getId());
        assertThat(created).extracting(ImportCandidate::getTargetType)
                .containsExactlyInAnyOrder(CandidateTargetType.values());
        assertThat(created).allMatch(c -> c.getStatus() == ImportCandidateStatus.PENDING);
        assertThat(careerRepository.count()).isEqualTo(careers);
        assertThat(certificationRepository.count()).isEqualTo(certifications);
        assertThat(educationRepository.count()).isEqualTo(educations);
        assertThat(awardRepository.count()).isEqualTo(awards);
        ImportBatch extracted = batchRepository.findById(batch.getId()).orElseThrow();
        assertThat(extracted.getExtractedAt()).isNotNull();
        assertThat(extracted.getExtractionProvider()).isEqualTo("anthropic");
        assertThat(extracted.getExtractionPromptVersion()).isEqualTo("v1");
    }

    @Test
    void validationFailureRollsBackEveryCandidateAndLeavesBatchRetryable() {
        ImportBatch batch = openBatch("validation raw");
        client.result = new StructuredExtractionResult(
                List.of(validCareer()), List.of(new CertificationCreateRequest("미상", null, null, null, null, null)),
                List.of(), List.of());

        assertStatus(() -> service.extract(batch.getId()), HttpStatus.BAD_REQUEST);
        assertThat(candidates(batch.getId())).isEmpty();
        assertThat(batchRepository.findById(batch.getId()).orElseThrow().getExtractedAt()).isNull();

        client.result = new StructuredExtractionResult(List.of(validCareer()), List.of(), List.of(), List.of());
        assertThat(service.extract(batch.getId()).createdCandidateCount()).isEqualTo(1);
    }

    @Test
    void mapsClientFailuresAndKeepsBatchRetryable() {
        for (var testCase : List.of(
                new FailureCase(LlmExtractionException.Reason.MALFORMED_RESPONSE, HttpStatus.BAD_REQUEST),
                new FailureCase(LlmExtractionException.Reason.PROVIDER_4XX, HttpStatus.BAD_REQUEST),
                new FailureCase(LlmExtractionException.Reason.NETWORK_TIMEOUT, HttpStatus.BAD_GATEWAY),
                new FailureCase(LlmExtractionException.Reason.PROVIDER_RETRY_EXHAUSTED, HttpStatus.SERVICE_UNAVAILABLE))) {
            ImportBatch batch = openBatch("failure " + testCase.reason());
            client.failure = new LlmExtractionException(testCase.reason());
            assertStatus(() -> service.extract(batch.getId()), testCase.status());
            assertThat(candidates(batch.getId())).isEmpty();
            assertThat(batchRepository.findById(batch.getId()).orElseThrow().getExtractedAt()).isNull();
        }
    }

    @Test
    void rejectsMissingCompletedAndAlreadyExtractedBatches() {
        assertStatus(() -> service.extract(Long.MAX_VALUE), HttpStatus.NOT_FOUND);
        ImportBatch completed = openBatch("completed");
        batchService.complete(completed.getId());
        assertStatus(() -> service.extract(completed.getId()), HttpStatus.CONFLICT);

        ImportBatch extracted = openBatch("once");
        service.extract(extracted.getId());
        assertStatus(() -> service.extract(extracted.getId()), HttpStatus.CONFLICT);
        assertThat(candidates(extracted.getId())).hasSize(4);
    }

    @Test
    void emptyStructuredResultIsSuccessful() {
        ImportBatch batch = openBatch("no facts");
        client.result = new StructuredExtractionResult(List.of(), List.of(), List.of(), List.of());
        ExtractionResponse response = service.extract(batch.getId());
        assertThat(response.createdCandidateCount()).isZero();
        assertThat(response.candidateIds()).isEmpty();
        assertThat(response.counts().values()).containsOnly(0);
        assertThat(batchRepository.findById(batch.getId()).orElseThrow().getExtractedAt()).isNotNull();
    }

    private ImportBatch openBatch(String rawText) {
        SourceDocument document = documentRepository.save(new SourceDocument(
                "source.txt", DocumentType.RESUME, "a".repeat(64), rawText));
        return batchRepository.saveAndFlush(new ImportBatch(document, ImportBatchStatus.OPEN));
    }

    private List<ImportCandidate> candidates(Long batchId) {
        return candidateRepository.findAll().stream()
                .filter(candidate -> candidate.getImportBatch().getId().equals(batchId)).toList();
    }

    private void assertStatus(org.assertj.core.api.ThrowableAssert.ThrowingCallable action, HttpStatus status) {
        assertThatThrownBy(action).isInstanceOfSatisfying(ResponseStatusException.class,
                exception -> assertThat(exception.getStatusCode()).isEqualTo(status));
    }

    private static StructuredExtractionResult validResult() {
        return new StructuredExtractionResult(List.of(validCareer()),
                List.of(new CertificationCreateRequest("정보처리기사", "기관", null, null, null, null)),
                List.of(new EducationCreateRequest("대학교", "컴퓨터", EducationDegree.BACHELOR,
                        EducationStatus.GRADUATED, null, null, null, null, null)),
                List.of(new AwardCreateRequest("대상", "기관", null, null)));
    }

    private static CareerExperienceCreateRequest validCareer() {
        return new CareerExperienceCreateRequest(ExperienceType.PROJECT, "프로젝트", "조직", "역할",
                LocalDate.of(2025, 1, 1), null, null, null, List.of(), List.of());
    }

    private record FailureCase(LlmExtractionException.Reason reason, HttpStatus status) {}

    @TestConfiguration
    static class FakeConfiguration {
        @Bean @Primary FakeClient fakeDocumentExtractionClient() { return new FakeClient(); }
    }

    static class FakeClient implements DocumentExtractionClient {
        StructuredExtractionResult result;
        LlmExtractionException failure;
        @Override public StructuredExtractionResult extract(String rawText, DocumentType documentType) {
            if (failure != null) throw failure;
            return result;
        }
    }
}
