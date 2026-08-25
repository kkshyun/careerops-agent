package com.careerops.backend.automation;

import com.careerops.backend.career.CareerExperience;
import com.careerops.backend.career.CareerExperienceRepository;
import com.careerops.backend.career.ExperienceType;
import com.careerops.backend.job.JobPosting;
import com.careerops.backend.job.JobPostingRepository;
import com.careerops.backend.notification.*;
import com.careerops.backend.notification.kakao.*;
import com.careerops.backend.recommend.JobRecommendationClient;
import com.careerops.backend.recommend.dto.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "careerops.kakao.initial-refresh-token=automation-fake-refresh",
        "careerops.automation.prepare.limit=5",
        "careerops.automation.delivery.limit=5"
})
class AutomationPipelineIntegrationTest {
    @Autowired AutomationPrepareService prepareService;
    @Autowired AutomationDeliveryService deliveryService;
    @Autowired JobRecommendationNotificationRepository notifications;
    @Autowired JobPostingRepository jobs;
    @Autowired CareerExperienceRepository experiences;
    @Autowired KakaoOauthTokenRepository oauthTokens;
    @Autowired FakeRecommendationClient recommendations;
    @Autowired FakeMessageClient messages;
    @Autowired FakeTokenClient tokens;

    private final List<JobPosting> createdJobs = new ArrayList<>();
    private CareerExperience evidence;

    @BeforeEach
    void setUp() {
        notifications.deleteAll();
        oauthTokens.deleteAll();
        evidence = experiences.saveAndFlush(new CareerExperience(ExperienceType.PROJECT,
                "automation approved evidence", "careerops", "developer", null, null,
                "verified project evidence", "verified project evidence"));
        for (int i = 1; i <= 3; i++) {
            String unique = UUID.randomUUID().toString();
            createdJobs.add(jobs.saveAndFlush(new JobPosting(
                    "automation-company-" + i, "AUTOMATION-001-job-" + unique,
                    null, null, null, "OPEN", null, "IT", null, null,
                    LocalDate.of(2026, 9, 30), "TEST",
                    "https://example.invalid/automation-pipeline/" + unique, unique)));
        }
        recommendations.reset();
        messages.calls.set(0);
        tokens.calls.set(0);
    }

    @AfterEach
    void tearDown() {
        notifications.deleteAll();
        oauthTokens.deleteAll();
        jobs.deleteAll(createdJobs);
        experiences.delete(evidence);
        createdJobs.clear();
    }

    @Test
    void prepareThenDeliveryMovesThreePendingToSentAndSecondRunIsIdempotent() {
        AutomationPrepareRunResult firstPrepare = prepareService.runOnce();

        assertThat(firstPrepare.succeeded()).isTrue();
        assertThat(firstPrepare.createdCount()).isEqualTo(3);
        assertThat(notifications.findAll()).allMatch(n -> n.getStatus() == NotificationStatus.PENDING);

        AutomationDeliveryRunResult firstDelivery = deliveryService.runOnce();

        assertThat(firstDelivery.sentCount()).isEqualTo(3);
        assertThat(notifications.findAll()).hasSize(3)
                .allMatch(n -> n.getStatus() == NotificationStatus.SENT);
        assertThat(messages.calls).hasValue(3);

        AutomationPrepareRunResult secondPrepare = prepareService.runOnce();
        AutomationDeliveryRunResult secondDelivery = deliveryService.runOnce();

        assertThat(secondPrepare.createdCount()).isZero();
        assertThat(secondPrepare.alreadyNotifiedCount()).isEqualTo(3);
        assertThat(secondDelivery.attemptedCount()).isZero();
        assertThat(notifications.findAll()).hasSize(3);
        assertThat(messages.calls).hasValue(3);
        assertThat(recommendations.calls).hasValue(2);
    }

    @TestConfiguration
    static class Config {
        @Bean @Primary FakeRecommendationClient fakeRecommendationClient() {
            return new FakeRecommendationClient();
        }

        @Bean @Primary FakeMessageClient fakeMessageClient() {
            return new FakeMessageClient();
        }

        @Bean @Primary FakeTokenClient fakeTokenClient() {
            return new FakeTokenClient();
        }
    }

    static class FakeRecommendationClient implements JobRecommendationClient {
        final AtomicInteger calls = new AtomicInteger();

        void reset() {
            calls.set(0);
        }

        @Override
        public RawRecommendationResult recommend(RecommendationInput input, int providerTopK) {
            calls.incrementAndGet();
            List<RawJobRecommendation> result = input.candidates().stream()
                    .filter(job -> job.title().startsWith("AUTOMATION-001-job-"))
                    .map(job -> new RawJobRecommendation(job.id(), 0.9, "verified automation evidence",
                            List.of(), List.of(), List.of(), List.of()))
                    .toList();
            return new RawRecommendationResult(result);
        }
    }

    static class FakeMessageClient implements KakaoMessageClient {
        final AtomicInteger calls = new AtomicInteger();

        @Override
        public void sendToMe(String accessToken, String text, String linkUrl) {
            calls.incrementAndGet();
        }
    }

    static class FakeTokenClient implements KakaoTokenClient {
        final AtomicInteger calls = new AtomicInteger();

        @Override
        public KakaoTokenRefreshResult refresh(String refreshToken) {
            calls.incrementAndGet();
            return new KakaoTokenRefreshResult("automation-fake-access", null, null);
        }
    }
}
