package com.careerops.backend.collector;

import com.careerops.backend.collector.alio.AlioJobDetailResponse;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;
import static org.assertj.core.api.Assertions.assertThat;

class AlioJobDetailResponseParsingTest {
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void parsesSingleResultWithStepsAndFiles() throws Exception {
        AlioJobDetailResponse response = AlioFixtureSupport.readDetail(objectMapper, "alio-detail-response-valid.json");
        assertThat(response.resultCode()).isEqualTo("200");
        assertThat(response.result().recrutPblntSn()).isEqualTo(1001L);
        assertThat(response.result().steps()).hasSize(2);
        assertThat(response.result().steps().getFirst().recrutStepSn()).isEqualTo(51001L);
        assertThat(response.result().steps().getFirst().recrutPbancTtl()).isEqualTo("정보기술");
        assertThat(response.result().steps().getFirst().rsnOcrnYmd()).isEqualTo("합성-원문");
        assertThat(response.result().files()).hasSize(3);
        assertThat(response.result().files().getFirst().recrutAtchFileNo()).isEqualTo(61001L);
        assertThat(response.result().files().getFirst().atchFileType()).isEqualTo("A");
    }
}
