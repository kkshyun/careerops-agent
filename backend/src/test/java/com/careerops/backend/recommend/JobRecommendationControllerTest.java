package com.careerops.backend.recommend;

import com.careerops.backend.recommend.dto.JobRecommendationResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class JobRecommendationControllerTest {
    @Autowired MockMvc mvc;
    @MockitoBean JobRecommendationService service;

    @Test
    void missingLimitUsesDefaultFive() throws Exception {
        when(service.recommend(5)).thenReturn(new JobRecommendationResponse(List.of()));

        mvc.perform(post("/api/jobs/recommendations"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.recommendations").isEmpty());

        verify(service).recommend(5);
    }

    @Test
    void maximumLimitTwentyIsAccepted() throws Exception {
        when(service.recommend(20)).thenReturn(new JobRecommendationResponse(List.of()));

        mvc.perform(post("/api/jobs/recommendations").param("limit", "20"))
                .andExpect(status().isOk());

        verify(service).recommend(20);
    }

    @Test
    void limitZeroIsBadRequestAndDoesNotCallService() throws Exception {
        mvc.perform(post("/api/jobs/recommendations").param("limit", "0"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(service);
    }

    @Test
    void limitTwentyOneIsBadRequestAndDoesNotCallService() throws Exception {
        mvc.perform(post("/api/jobs/recommendations").param("limit", "21"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(service);
    }
}
