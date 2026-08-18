package com.careerops.backend.pkbimport.extraction;

public class DocumentExtractionException extends RuntimeException {
    public DocumentExtractionException(String message) {
        super(message);
    }

    public DocumentExtractionException(String message, Throwable cause) {
        super(message, cause);
    }
}
