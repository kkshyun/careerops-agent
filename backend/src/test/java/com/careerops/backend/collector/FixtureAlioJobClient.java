package com.careerops.backend.collector;

import com.careerops.backend.collector.alio.AlioApiException;
import com.careerops.backend.collector.alio.AlioJobClient;
import com.careerops.backend.collector.alio.AlioJobListResponse;
import com.careerops.backend.collector.alio.AlioJobDetailResponse;
import java.util.*;

final class FixtureAlioJobClient implements AlioJobClient {

    private AlioJobListResponse response;
    private AlioApiException exception;
    private Integer lastNumOfRows;
    private final Map<Long, AlioJobDetailResponse> detailResponses = new HashMap<>();
    private final Map<Long, AlioApiException> detailFailures = new HashMap<>();
    private final List<Long> capturedDetailSns = new ArrayList<>();

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

    @Override
    public AlioJobDetailResponse fetchDetail(long sn) {
        capturedDetailSns.add(sn);
        if (detailFailures.containsKey(sn)) throw detailFailures.get(sn);
        AlioJobDetailResponse detail = detailResponses.get(sn);
        if (detail == null) throw new IllegalStateException("No detail fixture registered for sn=" + sn);
        return detail;
    }

    void respondToDetail(long sn, AlioJobDetailResponse response) { detailResponses.put(sn, response); detailFailures.remove(sn); }
    void failDetailWith(long sn, AlioApiException exception) { detailFailures.put(sn, exception); detailResponses.remove(sn); }
    List<Long> capturedDetailSns() { return List.copyOf(capturedDetailSns); }
    void resetDetails() { detailResponses.clear(); detailFailures.clear(); capturedDetailSns.clear(); }

    Integer lastNumOfRows() {
        return lastNumOfRows;
    }
}
