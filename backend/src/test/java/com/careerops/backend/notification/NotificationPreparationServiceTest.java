package com.careerops.backend.notification;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.careerops.backend.job.*;
import com.careerops.backend.notification.dto.NotificationPreparationResponse;
import com.careerops.backend.recommend.*;
import com.careerops.backend.recommend.dto.*;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.*;
import org.mockito.ArgumentCaptor;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class NotificationPreparationServiceTest {
    private JobRecommendationService recommendations;
    private JobRecommendationNotificationRepository repository;
    private JobPostingRepository jobs;
    private NotificationPreparationService service;
    private List<JobPosting> postings;

    @BeforeEach void setUp() {
        recommendations = mock(JobRecommendationService.class);
        repository = mock(JobRecommendationNotificationRepository.class);
        jobs = mock(JobPostingRepository.class);
        service = new NotificationPreparationService(recommendations, repository, jobs, new SimpleMeterRegistry());
        postings = java.util.stream.IntStream.rangeClosed(1, 20).mapToObj(i -> posting("OPEN", i)).toList();
        when(repository.findExistingJobPostingIds(anyCollection())).thenReturn(List.of());
        when(jobs.findAllById(any())).thenAnswer(invocation -> {
            Collection<Long> ids = invocation.getArgument(0);
            return postings.stream().filter(p -> ids.contains(p.getId())).toList();
        });
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test void createsFivePendingNotificationsInRecommendationOrder() {
        givenRecommendations(20);
        NotificationPreparationResponse result = service.prepare(5);
        assertThat(result.createdCount()).isEqualTo(5);
        assertThat(result.notifications()).extracting(v -> v.jobId()).containsExactly(1L, 2L, 3L, 4L, 5L);
        assertThat(result.notifications()).allMatch(v -> v.status() == NotificationStatus.PENDING);
    }

    @Test void skipsSomeDuplicatesAndFillsFromPool() {
        givenRecommendations(10); when(repository.findExistingJobPostingIds(anyCollection())).thenReturn(List.of(1L, 3L));
        NotificationPreparationResponse result = service.prepare(5);
        assertThat(result.alreadyNotifiedCount()).isEqualTo(2);
        assertThat(result.notifications()).extracting(v -> v.jobId()).containsExactly(2L, 4L, 5L, 6L, 7L);
    }

    @Test void allDuplicatesReturnCreatedCountZero() {
        givenRecommendations(3); when(repository.findExistingJobPostingIds(anyCollection())).thenReturn(List.of(1L, 2L, 3L));
        assertThat(service.prepare(5).createdCount()).isZero(); verify(repository, never()).save(any());
    }

    @Test void limitFiveTakesTopFive() { givenRecommendations(20); assertThat(service.prepare(5).notifications()).hasSize(5); }
    @Test void fewerUnseenDoesNotForceFill() { givenRecommendations(3); assertThat(service.prepare(20).createdCount()).isEqualTo(3); }
    @Test void preparationAlwaysRequestsTopTwenty() { givenRecommendations(3); service.prepare(5); verify(recommendations).recommend(20); }

    @Test void pkbConflictPropagatesWithoutRows() {
        when(recommendations.recommend(20)).thenThrow(new ResponseStatusException(HttpStatus.CONFLICT));
        assertThatThrownBy(() -> service.prepare(5)).isInstanceOf(ResponseStatusException.class); verify(repository, never()).save(any());
    }

    @Test void providerFailurePropagatesWithoutRows() {
        when(recommendations.recommend(20)).thenThrow(new JobRecommendationException(JobRecommendationException.Reason.NETWORK_TIMEOUT));
        assertThatThrownBy(() -> service.prepare(5)).isInstanceOf(JobRecommendationException.class); verify(repository, never()).save(any());
    }

    @Test void validationFailurePropagatesWithoutRows() {
        when(recommendations.recommend(20)).thenThrow(new JobRecommendationException(JobRecommendationException.Reason.UNKNOWN_JOB_ID));
        assertThatThrownBy(() -> service.prepare(5)).isInstanceOf(JobRecommendationException.class); verify(repository, never()).save(any());
    }

    @Test void noOpenJobsCreatesNothing() { givenRecommendations(3); postings = postings.stream().map(p -> posting("CLOSED", p.getId().intValue())).toList(); assertThat(service.prepare(5).createdCount()).isZero(); }

    @Test void closedDuringRefreshIsSkipped() {
        givenRecommendations(3); postings = List.of(posting("CLOSED", 1), posting("OPEN", 2), posting("OPEN", 3));
        assertThat(service.prepare(5).notifications()).extracting(v -> v.jobId()).containsExactly(2L, 3L);
    }

    @Test void secondRequestDoesNotRecreatePersistedRows() {
        givenRecommendations(2);
        assertThat(service.prepare(5).createdCount()).isEqualTo(2);
        when(repository.findExistingJobPostingIds(anyCollection())).thenReturn(List.of(1L, 2L));
        assertThat(service.prepare(5).createdCount()).isZero();
        verify(repository, times(2)).save(any());
    }

    @Test void failedExistingRowIsNotRecreated() { existingStatusStillDedupes(); }
    @Test void sentExistingRowIsNotRecreated() { existingStatusStillDedupes(); }
    private void existingStatusStillDedupes() { givenRecommendations(1); when(repository.findExistingJobPostingIds(anyCollection())).thenReturn(List.of(1L)); assertThat(service.prepare(5).createdCount()).isZero(); }

    @Test void recalculatedScoreDoesNotUpdateExistingRow() { existingStatusStillDedupes(); verify(repository, never()).save(any()); }

    @Test void truncatesReasonToTwoHundredCharacters() {
        when(recommendations.recommend(20)).thenReturn(new JobRecommendationResponse(List.of(recommendation(1, "x".repeat(201)))));
        service.prepare(5);
        ArgumentCaptor<JobRecommendationNotification> captor = ArgumentCaptor.forClass(JobRecommendationNotification.class);
        verify(repository).save(captor.capture()); assertThat(captor.getValue().getReason()).hasSize(200);
    }

    @Test void responseUsesRefreshedJobFields() {
        givenRecommendations(1); postings = List.of(posting("OPEN", 1));
        assertThat(service.prepare(5).notifications().getFirst()).satisfies(v -> { assertThat(v.companyName()).isEqualTo("company-1"); assertThat(v.title()).isEqualTo("title-1"); });
    }

    @Test void entityHasNoPkbIdFields() {
        assertThat(Arrays.stream(JobRecommendationNotification.class.getDeclaredFields()).map(java.lang.reflect.Field::getName))
                .noneMatch(name -> name.contains("careerExperience") || name.contains("certification") || name.contains("education") || name.contains("award"));
    }

    @Test void responseContainsOnlyNotificationAndCurrentJobFields() {
        assertThat(Arrays.stream(com.careerops.backend.notification.dto.JobRecommendationNotificationResponse.class.getRecordComponents()).map(java.lang.reflect.RecordComponent::getName))
                .containsExactly("id", "jobId", "companyName", "title", "applicationEndAt", "recommendationScore", "reason", "status", "createdAt");
    }

    @Test void logsDoNotExposeReasonCompanyOrTitle() {
        Logger logger = (Logger) LoggerFactory.getLogger(NotificationPreparationService.class); ListAppender<ILoggingEvent> appender = new ListAppender<>(); appender.start(); logger.addAppender(appender);
        try { when(recommendations.recommend(20)).thenReturn(new JobRecommendationResponse(List.of(recommendation(1, "SECRET_REASON")))); service.prepare(5);
            assertThat(appender.list).extracting(ILoggingEvent::getFormattedMessage).allMatch(m -> !m.contains("SECRET_REASON") && !m.contains("company-1") && !m.contains("title-1"));
        } finally { logger.detachAppender(appender); }
    }

    @Test void dedupePrecheckUsesOneBatchQuery() { givenRecommendations(20); service.prepare(5); verify(repository, times(1)).findExistingJobPostingIds(anyCollection()); }

    private void givenRecommendations(int count) { when(recommendations.recommend(20)).thenReturn(new JobRecommendationResponse(java.util.stream.IntStream.rangeClosed(1, count).mapToObj(i -> recommendation(i, "reason-" + i)).toList())); }
    private JobRecommendation recommendation(int id, String reason) { return new JobRecommendation((long) id, "stale-company", "stale-title", LocalDate.of(2026, 9, 1), 1d - id / 100d, reason, List.of(), List.of(), List.of(), List.of()); }
    private JobPosting posting(String status, int id) { JobPosting posting = new JobPosting("company-" + id, "title-" + id, null, null, null, status, null, null, null, null, LocalDate.of(2026, 9, 1), "TEST", "https://example.invalid/" + id, String.valueOf(id)); setId(posting, (long) id); return posting; }
    private void setId(JobPosting posting, long id) { try { var field = JobPosting.class.getDeclaredField("id"); field.setAccessible(true); field.set(posting, id); } catch (ReflectiveOperationException e) { throw new AssertionError(e); } }
}
