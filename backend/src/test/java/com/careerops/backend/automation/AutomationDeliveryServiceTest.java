package com.careerops.backend.automation;

import com.careerops.backend.notification.*;
import com.careerops.backend.notification.kakao.KakaoApiException;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class AutomationDeliveryServiceTest {
    private final JobRecommendationNotificationRepository repository = mock(JobRecommendationNotificationRepository.class);
    private final NotificationSendService sender = mock(NotificationSendService.class);
    private final SimpleMeterRegistry meters = new SimpleMeterRegistry();
    private AutomationDeliveryService service;

    @BeforeEach
    void setUp() {
        reset(repository, sender);
        service = new AutomationDeliveryService(repository, sender, meters, 3);
    }

    @Test
    void sendsPendingCandidatesSequentiallyUpToConfiguredLimit() {
        candidates(11L, 12L, 13L);

        AutomationDeliveryRunResult result = service.runOnce();

        assertThat(result).usingRecursiveComparison().ignoringFields("durationMs")
                .isEqualTo(new AutomationDeliveryRunResult(3, 3, 0, false, 0));
        var order = inOrder(sender);
        order.verify(sender).send(11L);
        order.verify(sender).send(12L);
        order.verify(sender).send(13L);
        verify(repository).findIdsByStatusOrderByCreatedAtAsc(NotificationStatus.PENDING, PageRequest.of(0, 3));
    }

    @Test
    void continuesAfterEveryBestEffortKakaoFailure() {
        candidates(1L, 2L, 3L);
        doThrow(delivery(KakaoApiException.Reason.PROVIDER_ERROR)).when(sender).send(1L);
        doThrow(delivery(KakaoApiException.Reason.PROVIDER_5XX)).when(sender).send(2L);
        doThrow(delivery(KakaoApiException.Reason.DELIVERY_UNKNOWN)).when(sender).send(3L);

        AutomationDeliveryRunResult result = service.runOnce();

        assertThat(result.attemptedCount()).isEqualTo(3);
        assertThat(result.failedCount()).isEqualTo(3);
        assertThat(result.shortCircuited()).isFalse();
        verify(sender, times(3)).send(anyLong());
    }

    @Test
    void tokenRefreshFailureShortCircuitsRemainingCandidates() {
        candidates(1L, 2L, 3L);
        doThrow(delivery(KakaoApiException.Reason.TOKEN_REFRESH_FAILED)).when(sender).send(2L);

        AutomationDeliveryRunResult result = service.runOnce();

        assertThat(result.attemptedCount()).isEqualTo(2);
        assertThat(result.sentCount()).isOne();
        assertThat(result.failedCount()).isOne();
        assertThat(result.shortCircuited()).isTrue();
        verify(sender, never()).send(3L);
        assertThat(meters.counter("careerops.automation.delivery.short_circuited",
                "reason", "token_refresh_failed").count()).isEqualTo(1);
    }

    @Test
    void skipsNotFoundAndConflictAndContinues() {
        candidates(1L, 2L, 3L);
        doThrow(new ResponseStatusException(HttpStatus.NOT_FOUND)).when(sender).send(1L);
        doThrow(new ResponseStatusException(HttpStatus.CONFLICT)).when(sender).send(2L);

        AutomationDeliveryRunResult result = service.runOnce();

        assertThat(result.attemptedCount()).isEqualTo(3);
        assertThat(result.sentCount()).isOne();
        assertThat(result.failedCount()).isZero();
        verify(sender).send(3L);
    }

    @Test
    void emptyBacklogDoesNotCallSender() {
        candidates();

        AutomationDeliveryRunResult result = service.runOnce();

        assertThat(result.attemptedCount()).isZero();
        verifyNoInteractions(sender);
        assertThat(meters.summary("careerops.automation.delivery.candidates").count()).isOne();
    }

    private void candidates(Long... ids) {
        when(repository.findIdsByStatusOrderByCreatedAtAsc(
                NotificationStatus.PENDING, PageRequest.of(0, 3))).thenReturn(List.of(ids));
    }

    private KakaoDeliveryException delivery(KakaoApiException.Reason reason) {
        return new KakaoDeliveryException(new KakaoApiException(reason, "fake"));
    }
}
