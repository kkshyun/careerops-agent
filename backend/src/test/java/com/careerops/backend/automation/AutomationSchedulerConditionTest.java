package com.careerops.backend.automation;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class AutomationSchedulerConditionTest {
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withBean(AutomationPrepareService.class, () -> mock(AutomationPrepareService.class))
            .withBean(AutomationDeliveryService.class, () -> mock(AutomationDeliveryService.class))
            .withUserConfiguration(AutomationPrepareScheduler.class, AutomationDeliveryScheduler.class)
            .withPropertyValues(
                    "careerops.automation.prepare.cron=0 50 7 * * *",
                    "careerops.automation.prepare.zone=Asia/Seoul",
                    "careerops.automation.delivery.cron=0 0 8 * * *",
                    "careerops.automation.delivery.zone=Asia/Seoul");

    @Test
    void bothSchedulersAreAbsentByDefault() {
        contextRunner.run(context -> {
            assertThat(context.getBeanNamesForType(AutomationPrepareScheduler.class)).isEmpty();
            assertThat(context.getBeanNamesForType(AutomationDeliveryScheduler.class)).isEmpty();
        });
    }

    @Test
    void onlyPrepareSchedulerExistsWhenOnlyPrepareIsEnabled() {
        contextRunner.withPropertyValues("careerops.automation.prepare.enabled=true").run(context -> {
            assertThat(context.getBeanNamesForType(AutomationPrepareScheduler.class)).hasSize(1);
            assertThat(context.getBeanNamesForType(AutomationDeliveryScheduler.class)).isEmpty();
        });
    }

    @Test
    void onlyDeliverySchedulerExistsWhenOnlyDeliveryIsEnabled() {
        contextRunner.withPropertyValues("careerops.automation.delivery.enabled=true").run(context -> {
            assertThat(context.getBeanNamesForType(AutomationPrepareScheduler.class)).isEmpty();
            assertThat(context.getBeanNamesForType(AutomationDeliveryScheduler.class)).hasSize(1);
        });
    }

    @Test
    void bothSchedulersExistWhenBothAreEnabled() {
        contextRunner.withPropertyValues(
                "careerops.automation.prepare.enabled=true",
                "careerops.automation.delivery.enabled=true").run(context -> {
            assertThat(context.getBeanNamesForType(AutomationPrepareScheduler.class)).hasSize(1);
            assertThat(context.getBeanNamesForType(AutomationDeliveryScheduler.class)).hasSize(1);
        });
    }
}
