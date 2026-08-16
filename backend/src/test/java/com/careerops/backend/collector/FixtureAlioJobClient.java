package com.careerops.backend.collector;

import com.careerops.backend.collector.alio.AlioApiException;
import com.careerops.backend.collector.alio.AlioJobClient;
import com.careerops.backend.collector.alio.AlioJobListResponse;
import com.careerops.backend.collector.alio.AlioJobDetailResponse;
import java.util.*;

final class FixtureAlioJobClient implements AlioJobClient {

    record ListCall(int pageNo, int numOfRows) {}

    private AlioJobListResponse response;
    private AlioApiException exception;
    private Integer lastNumOfRows;
    private final Map<Integer, AlioJobListResponse> pageResponses = new HashMap<>();
    private final Map<Integer, AlioApiException> pageFailures = new HashMap<>();
    private final List<ListCall> capturedCalls = new ArrayList<>();
    private final Map<Long, AlioJobDetailResponse> detailResponses = new HashMap<>();
    private final Map<Long, AlioApiException> detailFailures = new HashMap<>();
    private final List<Long> capturedDetailSns = new ArrayList<>();

    void respondWith(AlioJobListResponse response) {
        this.response = response;
        this.exception = null;
        this.pageResponses.clear();
        this.pageFailures.clear();
        this.capturedCalls.clear();
    }

    void failWith(AlioApiException exception) {
        this.exception = exception;
        this.response = null;
        this.pageResponses.clear();
        this.pageFailures.clear();
        this.capturedCalls.clear();
    }

    void respondToPage(int pageNo, AlioJobListResponse response) {
        this.exception = null;
        this.pageResponses.put(pageNo, response);
        this.pageFailures.remove(pageNo);
    }

    void failPageWith(int pageNo, AlioApiException exception) {
        this.pageFailures.put(pageNo, exception);
        this.pageResponses.remove(pageNo);
    }

    void resetList() {
        response = null;
        exception = null;
        lastNumOfRows = null;
        pageResponses.clear();
        pageFailures.clear();
        capturedCalls.clear();
    }

    @Override
    public AlioJobListResponse fetchList(int pageNo, int numOfRows) {
        this.lastNumOfRows = numOfRows;
        this.capturedCalls.add(new ListCall(pageNo, numOfRows));
        if (exception != null) {
            throw exception;
        }
        if (pageFailures.containsKey(pageNo)) throw pageFailures.get(pageNo);
        if (pageResponses.containsKey(pageNo)) return pageResponses.get(pageNo);
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

    List<ListCall> capturedCalls() { return List.copyOf(capturedCalls); }
}
