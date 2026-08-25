package com.careerops.backend.notification;

import com.careerops.backend.job.*;
import com.careerops.backend.notification.kakao.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.*;
import org.springframework.context.annotation.*;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.server.ResponseStatusException;
import java.time.*;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest(properties="careerops.kakao.initial-refresh-token=test-initial-refresh")
class NotificationSendIntegrationTest {
    @Autowired NotificationSendService service; @Autowired JobRecommendationNotificationRepository notifications;
    @Autowired JobPostingRepository jobs; @Autowired KakaoOauthTokenRepository oauthTokens;
    @Autowired CapturingMessageClient messages; @Autowired CapturingTokenClient tokens;
    JobPosting job; JobRecommendationNotification notification;
    @BeforeEach void setUp() {
        oauthTokens.deleteAll(); notifications.deleteAll();
        String unique=UUID.randomUUID().toString();
        job=jobs.saveAndFlush(new JobPosting("통합회사","통합제목",null,null,null,"OPEN",null,null,null,null,
                LocalDate.of(2026,9,30),"TEST","https://example.invalid/kakao/"+unique,unique));
        notification=notifications.saveAndFlush(new JobRecommendationNotification(job,.92,"통합근거"));
        messages.reset();tokens.reset();
    }
    @AfterEach void tearDown() { messages.release.countDown(); notifications.deleteAll(); oauthTokens.deleteAll(); jobs.delete(job); jobs.flush(); }

    @Test void concurrentSendClaimsExactlyOnceAndProviderRunsWithoutTransactionOrHeldRowLock() throws Exception {
        messages.block=true;
        try(ExecutorService executor=Executors.newFixedThreadPool(2)) {
            Future<Object> first=executor.submit(()->callSend(notification.getId()));
            assertThat(messages.entered.await(5,TimeUnit.SECONDS)).isTrue();
            assertThat(messages.transactionActive.get()).isFalse(); assertThat(tokens.transactionActive.get()).isFalse();
            assertThat(notifications.findById(notification.getId())).isPresent();
            Future<Object> second=executor.submit(()->callSend(notification.getId()));
            Object conflict=second.get(5,TimeUnit.SECONDS);
            assertThat(conflict).isInstanceOfSatisfying(ResponseStatusException.class,
                    e->assertThat(e.getStatusCode()).isEqualTo(HttpStatus.CONFLICT));
            messages.release.countDown(); assertThat(first.get(5,TimeUnit.SECONDS)).isInstanceOf(com.careerops.backend.notification.dto.NotificationSendResponse.class);
        }
        assertThat(messages.calls.get()).isOne();
        JobRecommendationNotification saved=notifications.findById(notification.getId()).orElseThrow();
        assertThat(saved.getStatus()).isEqualTo(NotificationStatus.SENT); assertThat(saved.getSentAt()).isNotNull();
        assertThatThrownBy(()->service.send(notification.getId())).isInstanceOfSatisfying(ResponseStatusException.class,
                e->assertThat(e.getStatusCode()).isEqualTo(HttpStatus.CONFLICT));
        assertThat(messages.calls.get()).isOne();
    }
    @Test void everyProviderAndTokenFailureIsCommittedBeforeBadGatewayAndFailedCanRetry() {
        for(KakaoApiException.Reason reason : new KakaoApiException.Reason[]{KakaoApiException.Reason.PROVIDER_ERROR,
                KakaoApiException.Reason.PROVIDER_5XX,KakaoApiException.Reason.DELIVERY_UNKNOWN}) {
            messages.failure=reason;
            assertThatThrownBy(()->service.send(notification.getId())).isInstanceOf(KakaoDeliveryException.class);
            assertFailed(reason.name());
        }
        messages.failure=null; tokens.failure=true;
        assertThatThrownBy(()->service.send(notification.getId())).isInstanceOf(KakaoDeliveryException.class);
        assertFailed("TOKEN_REFRESH_FAILED");
        tokens.failure=false; service.send(notification.getId());
        assertThat(notifications.findById(notification.getId()).orElseThrow().getStatus()).isEqualTo(NotificationStatus.SENT);
    }
    @Test void invalidMessageDataIsCommittedWithoutCallingEitherProvider() {
        JobPosting invalidJob=jobs.saveAndFlush(new JobPosting("c-null","t-null",null,null,null,"OPEN",null,null,null,null,
                LocalDate.now(),"TEST",null,UUID.randomUUID().toString()));
        JobRecommendationNotification invalid=notifications.saveAndFlush(new JobRecommendationNotification(invalidJob,.4,"r"));
        int tokenCalls=tokens.calls.get(), messageCalls=messages.calls.get();
        assertThatThrownBy(()->service.send(invalid.getId())).isInstanceOf(KakaoDeliveryException.class);
        JobRecommendationNotification saved=notifications.findById(invalid.getId()).orElseThrow();
        assertThat(saved.getStatus()).isEqualTo(NotificationStatus.FAILED);assertThat(saved.getFailureCode()).isEqualTo("INVALID_MESSAGE_DATA");
        assertThat(tokens.calls.get()).isEqualTo(tokenCalls);assertThat(messages.calls.get()).isEqualTo(messageCalls);
        notifications.delete(invalid);notifications.flush();jobs.delete(invalidJob);jobs.flush();
    }
    @Test void refreshRotationIsPersistedAndMissingRotationKeepsCurrentValue() {
        tokens.newRefreshToken="rotated-refresh"; service.send(notification.getId());
        assertThat(oauthTokens.findFirstByOrderByIdAsc().orElseThrow().getRefreshToken()).isEqualTo("rotated-refresh");
        JobPosting extraJob=jobs.saveAndFlush(new JobPosting("c2","t2",null,null,null,"OPEN",null,null,null,null,LocalDate.now(),"TEST",
                        "https://example.invalid/"+UUID.randomUUID(),UUID.randomUUID().toString()));
        JobRecommendationNotification extra=notifications.saveAndFlush(new JobRecommendationNotification(extraJob,.5,"r"));
        tokens.newRefreshToken=null; service.send(extra.getId());
        assertThat(oauthTokens.findFirstByOrderByIdAsc().orElseThrow().getRefreshToken()).isEqualTo("rotated-refresh");
        notifications.delete(extra); notifications.flush(); jobs.delete(extraJob); jobs.flush();
    }
    private Object callSend(long id) { try{return service.send(id);}catch(Throwable t){return t;} }
    private void assertFailed(String code){JobRecommendationNotification saved=notifications.findById(notification.getId()).orElseThrow();
        assertThat(saved.getStatus()).isEqualTo(NotificationStatus.FAILED);assertThat(saved.getFailureCode()).isEqualTo(code);}

    @TestConfiguration static class Config {
        @Bean @Primary CapturingMessageClient capturingMessageClient(){return new CapturingMessageClient();}
        @Bean @Primary CapturingTokenClient capturingTokenClient(){return new CapturingTokenClient();}
    }
    static class CapturingMessageClient implements KakaoMessageClient {
        final AtomicInteger calls=new AtomicInteger(); final AtomicBoolean transactionActive=new AtomicBoolean(true);
        volatile boolean block; volatile KakaoApiException.Reason failure; CountDownLatch entered,release;
        void reset(){calls.set(0);transactionActive.set(true);block=false;failure=null;entered=new CountDownLatch(1);release=new CountDownLatch(1);}
        public void sendToMe(String accessToken,String text,String linkUrl){calls.incrementAndGet();transactionActive.set(TransactionSynchronizationManager.isActualTransactionActive());entered.countDown();
            if(block)try{if(!release.await(5,TimeUnit.SECONDS))throw new IllegalStateException("release timeout");}catch(InterruptedException e){Thread.currentThread().interrupt();throw new IllegalStateException(e);}
            if(failure!=null)throw new KakaoApiException(failure,"fake failure");}
    }
    static class CapturingTokenClient implements KakaoTokenClient {
        final AtomicBoolean transactionActive=new AtomicBoolean(true); final AtomicInteger calls=new AtomicInteger(); volatile String newRefreshToken;volatile boolean failure;
        void reset(){transactionActive.set(true);calls.set(0);newRefreshToken=null;failure=false;}
        public KakaoTokenRefreshResult refresh(String refreshToken){calls.incrementAndGet();transactionActive.set(TransactionSynchronizationManager.isActualTransactionActive());
            if(failure)throw new KakaoApiException(KakaoApiException.Reason.TOKEN_REFRESH_FAILED,"fake token failure");
            return new KakaoTokenRefreshResult("fake-access",newRefreshToken,null);}
    }
}
