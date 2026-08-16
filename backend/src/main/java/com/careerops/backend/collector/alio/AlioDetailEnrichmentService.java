package com.careerops.backend.collector.alio;

import com.careerops.backend.job.*;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.List;

@Service
public class AlioDetailEnrichmentService {
    private static final Logger log = LoggerFactory.getLogger(AlioDetailEnrichmentService.class);

    private final AlioJobClient client;
    private final RecruitmentStepRepository stepRepository;
    private final AttachmentRepository attachmentRepository;
    private final JobPostingService jobPostingService;
    private final MeterRegistry meterRegistry;
    private final Counter stepsCounter;
    private final Counter filesCounter;
    private final Timer duration;
    private final TransactionTemplate transactionTemplate;

    public AlioDetailEnrichmentService(AlioJobClient client, RecruitmentStepRepository stepRepository,
                                       AttachmentRepository attachmentRepository, JobPostingService jobPostingService,
                                       MeterRegistry meterRegistry, PlatformTransactionManager transactionManager) {
        this.client = client; this.stepRepository = stepRepository; this.attachmentRepository = attachmentRepository;
        this.jobPostingService = jobPostingService; this.meterRegistry = meterRegistry;
        this.stepsCounter = Counter.builder("careerops.collector.detail.steps").register(meterRegistry);
        this.filesCounter = Counter.builder("careerops.collector.detail.files").register(meterRegistry);
        this.duration = Timer.builder("careerops.collector.detail.duration").register(meterRegistry);
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    public void enrich(JobPosting jobPosting) {
        final long sn;
        try {
            sn = Long.parseLong(jobPosting.getExternalId());
        } catch (RuntimeException exception) {
            return;
        }

        Timer.Sample sample = Timer.start(meterRegistry);
        try {
            AlioJobDetailResponse response = client.fetchDetail(sn);
            int[] saved = transactionTemplate.execute(status -> persistDetail(jobPosting, response));
            if (saved != null) {
                stepsCounter.increment(saved[0]);
                filesCounter.increment(saved[1]);
            }
            runCounter("success").increment();
        } catch (RuntimeException exception) {
            runCounter("failed").increment();
            log.warn("Failed to enrich ALIO job detail for sn={}", sn, exception);
        } finally {
            sample.stop(duration);
        }
    }

    private int[] persistDetail(JobPosting jobPosting, AlioJobDetailResponse response) {
        AlioJobDetailItem detail = response.result();
        List<AlioStepItem> steps = detail == null || detail.steps() == null ? List.of() : detail.steps();
        List<AlioFileItem> files = detail == null || detail.files() == null ? List.of() : detail.files();
        int savedSteps = 0;
        int savedFiles = 0;
        for (AlioStepItem step : steps) {
            if (step != null && !stepRepository.existsByRecrutStepSn(step.recrutStepSn())) {
                stepRepository.save(new RecruitmentStep(jobPosting, step.recrutStepSn(), step.sortNo(), step.minStepSn(),
                        step.maxStepSn(), step.recrutPbancTtl(), step.cmpttRt(), step.aplyNope(), step.recrutNope(), step.rsnOcrnYmd()));
                savedSteps++;
            }
        }
        for (AlioFileItem file : files) {
            if (file != null && !attachmentRepository.existsByRecrutAtchFileNo(file.recrutAtchFileNo())) {
                attachmentRepository.save(new Attachment(jobPosting, file.recrutAtchFileNo(), file.sortNo(),
                        file.atchFileNm(), file.atchFileType(), file.url()));
                savedFiles++;
            }
        }
        jobPostingService.markDetailFetched(jobPosting, Instant.now());
        return new int[]{savedSteps, savedFiles};
    }

    private Counter runCounter(String result) {
        return Counter.builder("careerops.collector.detail.run").tag("result", result).register(meterRegistry);
    }
}
