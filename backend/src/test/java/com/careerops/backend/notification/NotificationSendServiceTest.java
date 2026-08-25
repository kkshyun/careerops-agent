package com.careerops.backend.notification;

import ch.qos.logback.classic.*;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.careerops.backend.notification.kakao.*;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.*;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import java.time.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class NotificationSendServiceTest {
    NotificationDeliveryTransactions tx; KakaoTokenStore store; KakaoTokenClient tokens; KakaoMessageClient messages;
    SimpleMeterRegistry meters; NotificationSendService service; NotificationSendSnapshot snapshot;
    @BeforeEach void setUp() {
        tx=mock(NotificationDeliveryTransactions.class); store=mock(KakaoTokenStore.class);
        tokens=mock(KakaoTokenClient.class); messages=mock(KakaoMessageClient.class); meters=new SimpleMeterRegistry();
        service=new NotificationSendService(tx,store,tokens,messages,new KakaoRecommendationMessageFormatter(),meters);
        snapshot=new NotificationSendSnapshot(1L,2L,"민감회사","민감제목",LocalDate.of(2026,9,30),
                "https://example.invalid/1",0.91,"민감메시지본문",NotificationStatus.SENDING,null);
        when(tx.claim(eq(1L),any())).thenReturn(true); when(tx.snapshot(1)).thenReturn(Optional.of(snapshot));
        when(store.currentRefreshToken()).thenReturn("sensitive-refresh");
        when(tokens.refresh("sensitive-refresh")).thenReturn(new KakaoTokenRefreshResult("sensitive-access",null,null));
    }
    @Test void successMarksSentAndRecordsMetrics() {
        var response=service.send(1);
        assertThat(response.status()).isEqualTo(NotificationStatus.SENT); assertThat(response.sentAt()).isNotNull();
        verify(tx).sent(eq(1L),any()); verify(messages).sendToMe(eq("sensitive-access"),contains("민감회사"),eq(snapshot.sourceUrl()));
        assertThat(meters.get("careerops.kakao.send.request").tag("result","success").counter().count()).isEqualTo(1);
        assertThat(meters.get("careerops.kakao.token.refresh").tag("result","success").counter().count()).isEqualTo(1);
        assertThat(meters.get("careerops.kakao.send.duration").timer().count()).isEqualTo(1);
    }
    @Test void providerReasonsCommitFailedBeforeException() {
        for (KakaoApiException.Reason reason : List.of(KakaoApiException.Reason.PROVIDER_ERROR,
                KakaoApiException.Reason.PROVIDER_5XX,KakaoApiException.Reason.DELIVERY_UNKNOWN)) {
            reset(messages); doThrow(new KakaoApiException(reason,"secret provider body")).when(messages).sendToMe(any(),any(),any());
            assertThatThrownBy(()->service.send(1)).isInstanceOf(KakaoDeliveryException.class);
            var order=inOrder(tx); order.verify(tx).failed(1,reason.name());
        }
    }
    @Test void tokenFailureCommitsFailedAndSkipsMessage() {
        when(tokens.refresh(any())).thenThrow(new KakaoApiException(KakaoApiException.Reason.TOKEN_REFRESH_FAILED,"secret"));
        assertThatThrownBy(()->service.send(1)).isInstanceOf(KakaoDeliveryException.class);
        verify(tx).failed(1,"TOKEN_REFRESH_FAILED"); verifyNoInteractions(messages);
        assertThat(meters.get("careerops.kakao.token.refresh").tag("result","failure").counter().count()).isEqualTo(1);
    }
    @Test void missingSourceUrlFailsWithoutProviderCalls() {
        snapshot=new NotificationSendSnapshot(1L,2L,"c","t",null,null,.5,"r",NotificationStatus.SENDING,null);
        when(tx.snapshot(1)).thenReturn(Optional.of(snapshot));
        assertThatThrownBy(()->service.send(1)).isInstanceOf(KakaoDeliveryException.class);
        verify(tx).failed(1,"INVALID_MESSAGE_DATA"); verifyNoInteractions(tokens,messages);
    }
    @Test void missingNotificationIs404AndAlreadyClaimedIs409() {
        when(tx.claim(eq(1L),any())).thenReturn(false); when(tx.snapshot(1)).thenReturn(Optional.empty());
        assertThatThrownBy(()->service.send(1)).isInstanceOfSatisfying(ResponseStatusException.class,
                e->assertThat(e.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND)); verifyNoInteractions(tokens,messages);
        when(tx.snapshot(1)).thenReturn(Optional.of(snapshot));
        assertThatThrownBy(()->service.send(1)).isInstanceOfSatisfying(ResponseStatusException.class,
                e->assertThat(e.getStatusCode()).isEqualTo(HttpStatus.CONFLICT)); verifyNoInteractions(tokens,messages);
    }
    @Test void failedNotificationCanBeClaimedAndRetried() { assertThat(service.send(1).status()).isEqualTo(NotificationStatus.SENT); }
    @Test void rotatedRefreshTokenIsPersistedAndAbsentRotationIsIgnoredByStore() {
        Instant expiry=Instant.now().plusSeconds(100); when(tokens.refresh(any())).thenReturn(new KakaoTokenRefreshResult("a","new-refresh",expiry));
        service.send(1); verify(store).rotateIfPresent("new-refresh",expiry);
    }
    @Test void logsNeverContainSecretsOrMessageBody() {
        Logger logger=(Logger)org.slf4j.LoggerFactory.getLogger(NotificationSendService.class);
        ListAppender<ILoggingEvent> appender=new ListAppender<>();appender.start();logger.addAppender(appender);
        try { service.send(1); String logs=appender.list.stream().map(ILoggingEvent::getFormattedMessage).reduce("",String::concat);
            assertThat(logs).doesNotContain("sensitive-access","sensitive-refresh","민감회사","민감제목","민감메시지본문");
        } finally { logger.detachAppender(appender); }
    }
}
