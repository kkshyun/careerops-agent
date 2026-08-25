package com.careerops.backend.automation;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "careerops.automation.delivery", name = "enabled", havingValue = "true")
public class AutomationDeliveryScheduler {
    private final AutomationDeliveryService service;

    public AutomationDeliveryScheduler(AutomationDeliveryService service) {
        this.service = service;
    }

    @Scheduled(cron = "${careerops.automation.delivery.cron}", zone = "${careerops.automation.delivery.zone}")
    public void run() {
        service.runOnce();
    }
}
