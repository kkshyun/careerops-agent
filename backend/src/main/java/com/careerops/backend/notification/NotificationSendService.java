package com.careerops.backend.notification;

import com.careerops.backend.notification.dto.NotificationSendResponse;
import com.careerops.backend.notification.kakao.*;
import io.micrometer.core.instrument.*;
import org.slf4j.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import java.time.Instant;

@Service
public class NotificationSendService {
    private static final Logger log = LoggerFactory.getLogger(NotificationSendService.class);
    private final NotificationDeliveryTransactions transactions; private final KakaoTokenStore tokenStore;
    private final KakaoTokenClient tokenClient; private final KakaoMessageClient messageClient;
    private final KakaoRecommendationMessageFormatter formatter; private final MeterRegistry meters;
    public NotificationSendService(NotificationDeliveryTransactions transactions, KakaoTokenStore tokenStore,
            KakaoTokenClient tokenClient, KakaoMessageClient messageClient,
            KakaoRecommendationMessageFormatter formatter, MeterRegistry meters) {
        this.transactions = transactions; this.tokenStore = tokenStore; this.tokenClient = tokenClient;
        this.messageClient = messageClient; this.formatter = formatter; this.meters = meters;
    }
    public NotificationSendResponse send(long id) {
        Instant attemptedAt = Instant.now();
        if (!transactions.claim(id, attemptedAt)) {
            NotificationSendSnapshot current = transactions.snapshot(id)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Notification is already " + current.status());
        }
        Timer.Sample duration = Timer.start(meters);
        NotificationSendSnapshot snapshot = transactions.snapshot(id)
                .orElseThrow(() -> new IllegalStateException("Claimed notification disappeared: " + id));
        if (snapshot.sourceUrl() == null || snapshot.sourceUrl().isBlank())
            return fail(snapshot, "INVALID_MESSAGE_DATA", "invalid_message_data", duration, null);
        try {
            String refreshToken = tokenStore.currentRefreshToken();
            KakaoTokenRefreshResult token;
            try {
                token = tokenClient.refresh(refreshToken);
                meters.counter("careerops.kakao.token.refresh", "result", "success").increment();
            } catch (KakaoApiException exception) {
                meters.counter("careerops.kakao.token.refresh", "result", "failure").increment();
                throw exception;
            }
            tokenStore.rotateIfPresent(token.newRefreshToken(), token.newRefreshTokenExpiresAt());
            messageClient.sendToMe(token.accessToken(), formatter.format(snapshot), snapshot.sourceUrl());
            Instant sentAt = Instant.now(); transactions.sent(id, sentAt);
            meters.counter("careerops.kakao.send.request", "result", "success").increment();
            stop(duration); log.info("Kakao notification sent notificationId={} jobId={} success=true", id, snapshot.jobId());
            return new NotificationSendResponse(id, NotificationStatus.SENT, sentAt, snapshot.jobId());
        } catch (KakaoApiException exception) {
            return fail(snapshot, exception.reason().name(), exception.reason().metricTag(), duration, exception);
        }
    }
    private NotificationSendResponse fail(NotificationSendSnapshot snapshot, String code, String metric,
            Timer.Sample duration, Throwable cause) {
        transactions.failed(snapshot.notificationId(), code);
        meters.counter("careerops.kakao.send.request", "result", metric).increment(); stop(duration);
        log.warn("Kakao notification failed notificationId={} jobId={} failureCode={}",
                snapshot.notificationId(), snapshot.jobId(), code);
        if (cause == null) throw new KakaoDeliveryException("Kakao notification delivery failed");
        throw new KakaoDeliveryException(cause);
    }
    private void stop(Timer.Sample sample) { sample.stop(meters.timer("careerops.kakao.send.duration")); }
}
