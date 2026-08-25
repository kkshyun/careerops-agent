package com.careerops.backend.automation;

import com.careerops.backend.notification.JobRecommendationNotificationRepository;
import com.careerops.backend.notification.NotificationPreparationService;
import com.careerops.backend.notification.NotificationSendService;
import com.careerops.backend.notification.NotificationStatus;
import com.careerops.backend.notification.dto.NotificationPreparationResponse;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.core.annotation.AnnotatedElementUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class AutomationSchedulerTest {
    @Test
    void orchestrationServicesDoNotOwnTransactions() {
        assertThat(AnnotatedElementUtils.hasAnnotation(AutomationPrepareService.class, Transactional.class)).isFalse();
        assertThat(AnnotatedElementUtils.hasAnnotation(AutomationDeliveryService.class, Transactional.class)).isFalse();
    }

    @Test
    void prepareSchedulerDirectRunRecordsMetrics() {
        NotificationPreparationService preparation = mock(NotificationPreparationService.class);
        SimpleMeterRegistry meters = new SimpleMeterRegistry();
        when(preparation.prepare(5)).thenReturn(new NotificationPreparationResponse(1, 0, List.of()));
        AutomationPrepareScheduler scheduler = new AutomationPrepareScheduler(
                new AutomationPrepareService(preparation, meters, 5));

        scheduler.run();

        assertThat(meters.counter("careerops.automation.prepare.run", "result", "completed").count()).isOne();
        assertThat(meters.timer("careerops.automation.prepare.duration").count()).isOne();
    }

    @Test
    void deliverySchedulerDirectRunRecordsMetrics() {
        JobRecommendationNotificationRepository repository = mock(JobRecommendationNotificationRepository.class);
        NotificationSendService sender = mock(NotificationSendService.class);
        SimpleMeterRegistry meters = new SimpleMeterRegistry();
        when(repository.findIdsByStatusOrderByCreatedAtAsc(NotificationStatus.PENDING, PageRequest.of(0, 5)))
                .thenReturn(List.of());
        AutomationDeliveryScheduler scheduler = new AutomationDeliveryScheduler(
                new AutomationDeliveryService(repository, sender, meters, 5));

        scheduler.run();

        assertThat(meters.counter("careerops.automation.delivery.run", "result", "completed").count()).isOne();
        assertThat(meters.timer("careerops.automation.delivery.duration").count()).isOne();
        assertThat(meters.summary("careerops.automation.delivery.candidates").count()).isOne();
        verifyNoInteractions(sender);
    }
}
