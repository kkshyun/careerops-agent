package com.careerops.backend.pkbimport;

import com.careerops.backend.pkbimport.dto.ExtractionResponse;
import com.careerops.backend.pkbimport.dto.ImportCandidateCreateRequest;
import com.careerops.backend.pkbimport.extraction.llm.*;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@Service
public class ImportBatchExtractionService {
    private static final String PROVIDER = "anthropic";

    private final ImportBatchRepository batchRepository;
    private final ImportCandidateService importCandidateService;
    private final DocumentExtractionClient extractionClient;
    private final PlaceholderValueSanitizer sanitizer;
    private final ObjectMapper objectMapper;
    private final MeterRegistry meterRegistry;
    private final String model;

    public ImportBatchExtractionService(ImportBatchRepository batchRepository,
                                        ImportCandidateService importCandidateService,
                                        DocumentExtractionClient extractionClient,
                                        PlaceholderValueSanitizer sanitizer,
                                        ObjectMapper objectMapper,
                                        MeterRegistry meterRegistry,
                                        @Value("${careerops.ai.model}") String model) {
        this.batchRepository = batchRepository;
        this.importCandidateService = importCandidateService;
        this.extractionClient = extractionClient;
        this.sanitizer = sanitizer;
        this.objectMapper = objectMapper;
        this.meterRegistry = meterRegistry;
        this.model = model;
    }

    @Transactional
    public ExtractionResponse extract(Long batchId) {
        Timer.Sample sample = Timer.start(meterRegistry);
        try {
            ExtractionResponse response = doExtract(batchId);
            meterRegistry.counter("careerops.pkb.extraction.run", "result", "success").increment();
            return response;
        } catch (RuntimeException exception) {
            meterRegistry.counter("careerops.pkb.extraction.run", "result", "failure").increment();
            throw exception;
        } finally {
            sample.stop(meterRegistry.timer("careerops.pkb.extraction.duration"));
        }
    }

    private ExtractionResponse doExtract(Long batchId) {
        ImportBatch batch = batchRepository.findByIdForUpdate(batchId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        if (batch.getStatus() != ImportBatchStatus.OPEN) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "batch가 OPEN 상태가 아닙니다");
        }
        if (batch.getExtractedAt() != null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "이미 추출이 완료된 batch입니다");
        }

        StructuredExtractionResult result;
        try {
            result = extractionClient.extract(batch.getSourceDocument().getRawText(),
                    batch.getSourceDocument().getDocumentType());
        } catch (LlmExtractionException exception) {
            throw map(exception);
        }
        result = sanitizer.sanitize(result);

        List<Long> ids = new ArrayList<>();
        Map<CandidateTargetType, Integer> counts = new EnumMap<>(CandidateTargetType.class);
        for (CandidateTargetType type : CandidateTargetType.values()) counts.put(type, 0);

        result.careerExperiences().forEach(request -> create(batchId, CandidateTargetType.CAREER_EXPERIENCE,
                request, ids, counts));
        result.certifications().forEach(request -> create(batchId, CandidateTargetType.CERTIFICATION,
                request, ids, counts));
        result.educations().forEach(request -> create(batchId, CandidateTargetType.EDUCATION,
                request, ids, counts));
        result.awards().forEach(request -> create(batchId, CandidateTargetType.AWARD, request, ids, counts));

        batch.markExtracted(PROVIDER, model, ExtractionPromptBuilder.PROMPT_VERSION);
        counts.forEach((type, count) -> meterRegistry.counter("careerops.pkb.extraction.candidates",
                "targetType", type.name()).increment(count));
        return new ExtractionResponse(batchId, ids.size(), Map.copyOf(counts), List.copyOf(ids));
    }

    private void create(Long batchId, CandidateTargetType type, Object request, List<Long> ids,
                        Map<CandidateTargetType, Integer> counts) {
        final String payload;
        try {
            payload = objectMapper.writeValueAsString(request);
        } catch (Exception exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "추출 결과를 처리할 수 없습니다");
        }
        Long id = importCandidateService.create(batchId, new ImportCandidateCreateRequest(type, payload)).id();
        ids.add(id);
        counts.compute(type, (ignored, count) -> count + 1);
    }

    private ResponseStatusException map(LlmExtractionException exception) {
        return switch (exception.reason()) {
            case NETWORK_TIMEOUT -> new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "AI 제공자 연결에 실패했습니다");
            case PROVIDER_4XX -> new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "AI 제공자가 요청을 거부했습니다");
            case PROVIDER_RETRY_EXHAUSTED -> new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "AI 제공자를 일시적으로 사용할 수 없습니다");
            case MALFORMED_RESPONSE -> new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "AI 추출 결과 형식이 올바르지 않습니다");
        };
    }
}
