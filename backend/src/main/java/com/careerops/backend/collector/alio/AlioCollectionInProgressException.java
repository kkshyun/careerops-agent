package com.careerops.backend.collector.alio;

public class AlioCollectionInProgressException extends RuntimeException {

    public AlioCollectionInProgressException() {
        super("ALIO collection already in progress");
    }
}
