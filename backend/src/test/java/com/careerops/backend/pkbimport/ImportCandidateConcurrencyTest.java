package com.careerops.backend.pkbimport;

import com.careerops.backend.career.AwardRepository;
import com.careerops.backend.pkbimport.dto.ImportCandidateCreateRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.ArrayList;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class ImportCandidateConcurrencyTest {
    @Autowired SourceDocumentRepository documentRepository;
    @Autowired ImportBatchRepository batchRepository;
    @Autowired ImportCandidateRepository candidateRepository;
    @Autowired ImportCandidateService candidateService;
    @Autowired ImportBatchService batchService;
    @Autowired AwardRepository awardRepository;

    private final List<Long> createdBatchIds = new ArrayList<>();
    private final List<Long> createdDocumentIds = new ArrayList<>();

    @AfterEach
    void cleanUp() {
        for (Long batchId : createdBatchIds) {
            List<ImportCandidate> candidates = candidateRepository
                    .search(batchId, null, org.springframework.data.domain.Pageable.unpaged()).getContent();
            awardRepository.deleteAllById(candidates.stream()
                    .filter(candidate -> candidate.getTargetType() == CandidateTargetType.AWARD)
                    .map(ImportCandidate::getCreatedEntityId).filter(java.util.Objects::nonNull).toList());
            awardRepository.flush();
            candidateRepository.deleteAll(candidates);
            candidateRepository.flush();
        }
        batchRepository.deleteAllById(createdBatchIds);
        batchRepository.flush();
        documentRepository.deleteAllById(createdDocumentIds);
        documentRepository.flush();
    }

    @Test
    void concurrentApproveCreatesExactlyOnePkbRow() throws Exception {
        ImportBatch batch = batch("approve");
        ImportCandidate candidate = candidateRepository.save(new ImportCandidate(batch, CandidateTargetType.AWARD,
                "{\"title\":\"concurrent-" + System.nanoTime() + "\"}"));
        long before = awardRepository.count();
        AtomicInteger successes = new AtomicInteger();
        AtomicInteger conflicts = new AtomicInteger();
        runTogether(() -> approve(batch.getId(), candidate.getId(), successes, conflicts),
                () -> approve(batch.getId(), candidate.getId(), successes, conflicts));
        assertThat(successes).hasValue(1);
        assertThat(conflicts).hasValue(1);
        assertThat(awardRepository.count()).isEqualTo(before + 1);
    }

    @Test
    void concurrentCompleteAndCreatePreserveBatchInvariant() throws Exception {
        ImportBatch batch = batch("complete-create");
        AtomicInteger completeSuccess = new AtomicInteger();
        AtomicInteger createSuccess = new AtomicInteger();
        AtomicInteger conflicts = new AtomicInteger();
        runTogether(() -> {
            try { batchService.complete(batch.getId()); completeSuccess.incrementAndGet(); }
            catch (ResponseStatusException e) { if (e.getStatusCode().value() == 409) conflicts.incrementAndGet(); else throw e; }
        }, () -> {
            try {
                candidateService.create(batch.getId(), new ImportCandidateCreateRequest(CandidateTargetType.AWARD,
                        "{\"title\":\"race\"}"));
                createSuccess.incrementAndGet();
            } catch (ResponseStatusException e) { if (e.getStatusCode().value() == 409) conflicts.incrementAndGet(); else throw e; }
        });
        ImportBatch finalBatch = batchRepository.findById(batch.getId()).orElseThrow();
        boolean pending = candidateRepository.existsByImportBatchIdAndStatus(batch.getId(), ImportCandidateStatus.PENDING);
        assertThat(completeSuccess.get() + createSuccess.get() + conflicts.get()).isEqualTo(2);
        if (completeSuccess.get() == 1) {
            assertThat(finalBatch.getStatus()).isEqualTo(ImportBatchStatus.COMPLETED);
            assertThat(pending).isFalse();
        } else {
            assertThat(createSuccess).hasValue(1);
            assertThat(finalBatch.getStatus()).isEqualTo(ImportBatchStatus.OPEN);
            assertThat(pending).isTrue();
        }
    }

    private void approve(Long batchId, Long candidateId, AtomicInteger successes, AtomicInteger conflicts) {
        try { candidateService.approve(batchId, candidateId); successes.incrementAndGet(); }
        catch (ResponseStatusException e) { if (e.getStatusCode().value() == 409) conflicts.incrementAndGet(); else throw e; }
    }
    private void runTogether(Runnable first, Runnable second) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2); CountDownLatch start = new CountDownLatch(1);
        Callable<Void> a = () -> { ready.countDown(); start.await(); first.run(); return null; };
        Callable<Void> b = () -> { ready.countDown(); start.await(); second.run(); return null; };
        List<Future<Void>> futures = List.of(executor.submit(a), executor.submit(b));
        assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue(); start.countDown();
        for (Future<Void> future : futures) future.get(10, TimeUnit.SECONDS);
        executor.shutdownNow();
    }
    private ImportBatch batch(String name) {
        SourceDocument document = documentRepository.save(new SourceDocument(name + System.nanoTime(),
                DocumentType.OTHER, "c".repeat(64), "raw"));
        ImportBatch batch = batchRepository.save(new ImportBatch(document, ImportBatchStatus.OPEN));
        createdDocumentIds.add(document.getId());
        createdBatchIds.add(batch.getId());
        return batch;
    }
}
