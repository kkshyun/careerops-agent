package com.careerops.backend.collector;

import com.careerops.backend.collector.alio.AlioApiException;
import com.careerops.backend.collector.alio.AlioJobClient;
import com.careerops.backend.collector.alio.AlioJobListResponse;
import com.careerops.backend.collector.alio.AlioJobDetailResponse;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

final class FixtureAlioJobClient implements AlioJobClient {

    record ListCall(int pageNo, int numOfRows) {}

    private volatile AlioJobListResponse response;
    private volatile AlioApiException exception;
    private volatile Integer lastNumOfRows;
    private final Map<Integer, AlioJobListResponse> pageResponses = new HashMap<>();
    private final Map<Integer, AlioApiException> pageFailures = new HashMap<>();
    private final List<ListCall> capturedCalls = Collections.synchronizedList(new ArrayList<>());
    private final Map<Long, AlioJobDetailResponse> detailResponses = new HashMap<>();
    private final Map<Long, AlioApiException> detailFailures = new HashMap<>();
    private final List<Long> capturedDetailSns = Collections.synchronizedList(new ArrayList<>());
    private volatile CountDownLatch fetchEntered;
    private volatile CountDownLatch fetchRelease;

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
        releaseBlockedFetch();
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
        CountDownLatch entered = fetchEntered;
        CountDownLatch release = fetchRelease;
        if (entered != null && release != null) {
            entered.countDown();
            try {
                if (!release.await(10, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("Timed out waiting to release fixture list fetch");
                }
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Interrupted while waiting to release fixture list fetch", exception);
            }
        }
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

    void blockNextFetch() {
        fetchEntered = new CountDownLatch(1);
        fetchRelease = new CountDownLatch(1);
    }

    boolean awaitFetchEntered() throws InterruptedException {
        return fetchEntered != null && fetchEntered.await(5, TimeUnit.SECONDS);
    }

    void releaseBlockedFetch() {
        CountDownLatch release = fetchRelease;
        if (release != null) release.countDown();
        fetchEntered = null;
        fetchRelease = null;
    }
}
