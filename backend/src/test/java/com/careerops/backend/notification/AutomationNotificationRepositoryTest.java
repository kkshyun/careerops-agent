package com.careerops.backend.notification;

import com.careerops.backend.job.JobPosting;
import com.careerops.backend.job.JobPostingRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional
class AutomationNotificationRepositoryTest {
    @Autowired JobRecommendationNotificationRepository notifications;
    @Autowired JobPostingRepository jobs;
    @Autowired JdbcTemplate jdbc;
    @Autowired EntityManager entityManager;

    @Test
    void selectsOnlyPendingOldestFirstAndAppliesPageLimit() {
        List<JobRecommendationNotification> rows = new ArrayList<>();
        rows.add(save(NotificationStatus.PENDING));
        rows.add(save(NotificationStatus.SENT));
        rows.add(save(NotificationStatus.SENDING));
        rows.add(save(NotificationStatus.PENDING));
        rows.add(save(NotificationStatus.FAILED));
        rows.add(save(NotificationStatus.PENDING));
        notifications.flush();
        Instant base = Instant.parse("2026-08-25T00:00:00Z");
        for (int i = 0; i < rows.size(); i++) {
            jdbc.update("UPDATE job_recommendation_notifications SET created_at = ? WHERE id = ?",
                    java.sql.Timestamp.from(base.plusSeconds(i)), rows.get(i).getId());
        }
        entityManager.clear();

        List<Long> result = notifications.findIdsByStatusOrderByCreatedAtAsc(
                NotificationStatus.PENDING, PageRequest.of(0, 2));

        assertThat(result).containsExactly(rows.get(0).getId(), rows.get(3).getId());
    }

    private JobRecommendationNotification save(NotificationStatus status) {
        String unique = UUID.randomUUID().toString();
        JobPosting job = jobs.save(new JobPosting("automation-company", "automation-title", null, null, null,
                "OPEN", null, null, null, null, LocalDate.of(2026, 9, 30), "TEST",
                "https://example.invalid/automation/" + unique, unique));
        return notifications.save(new JobRecommendationNotification(job, 0.8, "evidence", status));
    }
}
