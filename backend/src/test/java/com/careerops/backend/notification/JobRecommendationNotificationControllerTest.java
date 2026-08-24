package com.careerops.backend.notification;

import com.careerops.backend.notification.dto.*;
import com.careerops.backend.recommend.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class JobRecommendationNotificationControllerTest {
    @MockitoBean NotificationPreparationService service;
    @Autowired MockMvc mvc;

    @BeforeEach void setUp() {
        when(service.prepare(anyInt())).thenReturn(new NotificationPreparationResponse(0, 0, List.of()));
        when(service.search(any(), any())).thenReturn(new JobRecommendationNotificationListResponse(List.of(), 0, 0, 0, 20));
    }

    @Test void defaultLimitIsFive() throws Exception { mvc.perform(post("/api/notifications/job-recommendations")).andExpect(status().isOk()); verify(service).prepare(5); }
    @Test void maxLimitTwentyIsAccepted() throws Exception { mvc.perform(post("/api/notifications/job-recommendations?limit=20")).andExpect(status().isOk()); verify(service).prepare(20); }
    @Test void zeroLimitIsBadRequest() throws Exception { mvc.perform(post("/api/notifications/job-recommendations?limit=0")).andExpect(status().isBadRequest()); verify(service, never()).prepare(anyInt()); }
    @Test void twentyOneLimitIsBadRequest() throws Exception { mvc.perform(post("/api/notifications/job-recommendations?limit=21")).andExpect(status().isBadRequest()); verify(service, never()).prepare(anyInt()); }
    @Test void recommendationFailureIsBadGateway() throws Exception { when(service.prepare(5)).thenThrow(new JobRecommendationException(JobRecommendationException.Reason.NETWORK_TIMEOUT)); mvc.perform(post("/api/notifications/job-recommendations")).andExpect(status().isBadGateway()); }
    @Test void pkbConflictIsPreserved() throws Exception { when(service.prepare(5)).thenThrow(new ResponseStatusException(HttpStatus.CONFLICT)); mvc.perform(post("/api/notifications/job-recommendations")).andExpect(status().isConflict()); }
    @Test void statusFilterIsPassedToReadService() throws Exception { mvc.perform(get("/api/notifications/job-recommendations?status=PENDING")).andExpect(status().isOk()); verify(service).search(eq(NotificationStatus.PENDING), any(Pageable.class)); }
    @Test void readApiUsesPageable() throws Exception { mvc.perform(get("/api/notifications/job-recommendations?page=2&size=7")).andExpect(status().isOk()); verify(service).search(isNull(), argThat(p -> p.getPageNumber() == 2 && p.getPageSize() == 7)); }
}
