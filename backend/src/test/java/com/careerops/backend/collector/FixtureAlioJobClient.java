package com.careerops.backend.collector;

import com.careerops.backend.collector.alio.AlioApiException;
import com.careerops.backend.collector.alio.AlioJobClient;
import com.careerops.backend.collector.alio.AlioJobListResponse;

final class FixtureAlioJobClient implements AlioJobClient {

    private AlioJobListResponse response;
    private AlioApiException exception;
    private Integer lastNumOfRows;

    void respondWith(AlioJobListResponse response) {
        this.response = response;
        this.exception = null;
    }

    void failWith(AlioApiException exception) {
        this.exception = exception;
        this.response = null;
    }

    @Override
    public AlioJobListResponse fetchList(int pageNo, int numOfRows) {
        this.lastNumOfRows = numOfRows;
        if (exception != null) {
            throw exception;
        }
        return response;
    }

    Integer lastNumOfRows() {
        return lastNumOfRows;
    }
}
