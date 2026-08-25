package com.careerops.backend.automation;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "careerops.automation.prepare", name = "enabled", havingValue = "true")
public class AutomationPrepareScheduler {
    private final AutomationPrepareService service;

    public AutomationPrepareScheduler(AutomationPrepareService service) {
        this.service = service;
    }

    @Scheduled(cron = "${careerops.automation.prepare.cron}", zone = "${careerops.automation.prepare.zone}")
    public void run() {
        service.runOnce();
    }
}
