package com.careerops.backend.notification;

import com.careerops.backend.job.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.LocalDate;
import java.util.UUID;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.*;

@SpringBootTest
class JobRecommendationNotificationDatabaseTest {
    @Autowired private JobRecommendationNotificationRepository repository;
    @Autowired private JobPostingRepository jobs;
    private JobPosting posting;

    @BeforeEach void createPosting() {
        posting = jobs.saveAndFlush(new JobPosting("NOTIFY DB company", "NOTIFY DB title", null, null, null,
                "OPEN", null, null, null, null, LocalDate.of(2026, 9, 30), "TEST",
                "https://example.invalid/notify-001/" + UUID.randomUUID(), UUID.randomUUID().toString()));
    }

    @AfterEach void cleanUp() {
        repository.deleteAll(); repository.flush();
        jobs.delete(posting); jobs.flush();
    }

    @Test void uniqueConstraintRejectsSecondSaveAndFlush() {
        repository.saveAndFlush(new JobRecommendationNotification(posting, 0.9, "first"));
        assertThatThrownBy(() -> repository.saveAndFlush(new JobRecommendationNotification(posting, 0.8, "second")))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThat(repository.countByJobPostingId(posting.getId())).isOne();
    }

    @Test void concurrentInsertsLeaveExactlyOneRow() throws Exception {
        CyclicBarrier barrier = new CyclicBarrier(2);
        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            Future<Boolean> first = executor.submit(() -> insertAfterBarrier(barrier, 0.9));
            Future<Boolean> second = executor.submit(() -> insertAfterBarrier(barrier, 0.8));
            assertThat(first.get(10, TimeUnit.SECONDS) ^ second.get(10, TimeUnit.SECONDS)).isTrue();
        }
        assertThat(repository.countByJobPostingId(posting.getId())).isOne();
    }

    private boolean insertAfterBarrier(CyclicBarrier barrier, double score) throws Exception {
        barrier.await(5, TimeUnit.SECONDS);
        try {
            repository.saveAndFlush(new JobRecommendationNotification(posting, score, "concurrent"));
            return true;
        } catch (DataIntegrityViolationException exception) {
            return false;
        }
    }
}
