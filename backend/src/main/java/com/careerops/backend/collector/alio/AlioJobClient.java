package com.careerops.backend.collector.alio;

public interface AlioJobClient {
    AlioJobListResponse fetchList(int pageNo, int numOfRows);
    AlioJobDetailResponse fetchDetail(long sn);
}
