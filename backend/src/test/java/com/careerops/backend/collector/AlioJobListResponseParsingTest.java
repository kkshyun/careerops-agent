package com.careerops.backend.collector;

import com.careerops.backend.collector.alio.AlioJobListResponse;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;

class AlioJobListResponseParsingTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void parsesNestedItemResponse() throws Exception {
        AlioJobListResponse response = AlioFixtureSupport.read(objectMapper, "alio-list-response-valid.json");

        assertThat(response.resultCode()).isEqualTo("200");
        assertThat(response.totalCount()).isEqualTo(2);
        assertThat(response.result()).hasSize(2);
        assertThat(response.result().getFirst().instNm()).isEqualTo("합성 공공기관 A");
        assertThat(response.result().getFirst().recrutPblntSn()).isEqualTo(1001L);
        assertThat(response.result().getFirst().pbancBgngYmd()).isEqualTo("20260801");
    }
}
