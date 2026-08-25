package com.careerops.backend.notification.dto;

import com.careerops.backend.notification.NotificationStatus;
import java.time.Instant;

public record NotificationSendResponse(long notificationId, NotificationStatus status, Instant sentAt, long jobId) {}
