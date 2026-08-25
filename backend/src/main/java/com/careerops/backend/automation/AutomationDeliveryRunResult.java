package com.careerops.backend.automation;

public record AutomationDeliveryRunResult(
        int attemptedCount, int sentCount, int failedCount, boolean shortCircuited, long durationMs) {
}
