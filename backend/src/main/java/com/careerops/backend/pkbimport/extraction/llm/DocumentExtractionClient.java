package com.careerops.backend.pkbimport.extraction.llm;

import com.careerops.backend.pkbimport.DocumentType;

public interface DocumentExtractionClient {
    StructuredExtractionResult extract(String rawText, DocumentType documentType);
}
