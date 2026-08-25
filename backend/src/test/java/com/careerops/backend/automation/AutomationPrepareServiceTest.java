package com.careerops.backend.automation;

import com.careerops.backend.notification.NotificationPreparationService;
import com.careerops.backend.notification.dto.NotificationPreparationResponse;
import com.careerops.backend.recommend.JobRecommendationException;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.*;

class AutomationPrepareServiceTest {
    private final NotificationPreparationService preparation = mock(NotificationPreparationService.class);
    private final SimpleMeterRegistry meters = new SimpleMeterRegistry();
    private AutomationPrepareService service;

    @BeforeEach
    void setUp() {
        reset(preparation);
        service = new AutomationPrepareService(preparation, meters, 7);
    }

    @Test
    void delegatesConfiguredLimitAndReturnsCounts() {
        when(preparation.prepare(7)).thenReturn(new NotificationPreparationResponse(3, 2, List.of()));

        AutomationPrepareRunResult result = service.runOnce();

        assertThat(result.succeeded()).isTrue();
        assertThat(result.createdCount()).isEqualTo(3);
        assertThat(result.alreadyNotifiedCount()).isEqualTo(2);
        assertThat(result.durationMs()).isNotNegative();
        verify(preparation).prepare(7);
        verifyNoMoreInteractions(preparation);
        assertThat(counter("completed")).isEqualTo(1);
    }

    @Test
    void catchesConflictAndReturnsFailedResult() {
        when(preparation.prepare(7)).thenThrow(new ResponseStatusException(HttpStatus.CONFLICT));

        assertFailedWithoutThrowing();
    }

    @Test
    void catchesRepairableProviderFailure() {
        when(preparation.prepare(7)).thenThrow(
                new JobRecommendationException(JobRecommendationException.Reason.MALFORMED_RESPONSE));

        assertFailedWithoutThrowing();
    }

    @Test
    void catchesValidationFailure() {
        when(preparation.prepare(7)).thenThrow(
                new JobRecommendationException(JobRecommendationException.Reason.UNKNOWN_JOB_ID));

        assertFailedWithoutThrowing();
    }

    private void assertFailedWithoutThrowing() {
        final AutomationPrepareRunResult[] value = new AutomationPrepareRunResult[1];
        assertThatCode(() -> value[0] = service.runOnce()).doesNotThrowAnyException();
        assertThat(value[0].succeeded()).isFalse();
        assertThat(value[0].createdCount()).isZero();
        assertThat(value[0].alreadyNotifiedCount()).isZero();
        assertThat(counter("failed")).isEqualTo(1);
    }

    private double counter(String result) {
        return meters.counter("careerops.automation.prepare.run", "result", result).count();
    }
}
