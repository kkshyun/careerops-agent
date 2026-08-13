package com.careerops.backend.collector;

import com.careerops.backend.collector.alio.AlioApiException;
import com.careerops.backend.job.JobPostingRepository;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.annotation.Rollback;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(AlioTestConfiguration.class)
@Transactional
@Rollback
class CollectControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private FixtureAlioJobClient client;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private JobPostingRepository repository;
    @Autowired private MeterRegistry meterRegistry;

    @Test
    void collectsAlioCaseInsensitively() throws Exception {
        client.respondWith(AlioFixtureSupport.read(objectMapper, "alio-list-response-valid.json"));
        long rowsBefore = repository.count();

        mockMvc.perform(post("/api/collect/ALIO").param("numOfRows", "25"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.source").value("ALIO"))
                .andExpect(jsonPath("$.fetched").value(2))
                .andExpect(jsonPath("$.saved").value(2))
                .andExpect(jsonPath("$.failed").value(0));

        assertThat(repository.count()).isEqualTo(rowsBefore + 2);
    }

    @Test
    void returnsBadGatewayWithoutSavingWhenClientFails() throws Exception {
        client.failWith(new AlioApiException(AlioApiException.Reason.FETCH_ERROR, "fixture failure"));
        long rowsBefore = repository.count();
        double runBefore = meterRegistry.counter("careerops.collector.run", "source", "alio", "result", "failed").count();

        mockMvc.perform(post("/api/collect/alio"))
                .andExpect(status().isBadGateway());

        assertThat(repository.count()).isEqualTo(rowsBefore);
        assertThat(meterRegistry.counter("careerops.collector.run", "source", "alio", "result", "failed").count())
                .isEqualTo(runBefore + 1);
    }

    @Test
    void rejectsUnsupportedSourceWithoutSaving() throws Exception {
        long rowsBefore = repository.count();

        mockMvc.perform(post("/api/collect/unknown"))
                .andExpect(status().isBadRequest());

        assertThat(repository.count()).isEqualTo(rowsBefore);
    }
}
