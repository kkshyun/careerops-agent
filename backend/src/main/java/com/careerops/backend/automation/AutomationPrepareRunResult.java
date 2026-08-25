package com.careerops.backend.automation;

public record AutomationPrepareRunResult(
        boolean succeeded, int createdCount, int alreadyNotifiedCount, long durationMs) {
}
