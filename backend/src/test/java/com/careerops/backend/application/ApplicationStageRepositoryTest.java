package com.careerops.backend.application;

import com.careerops.backend.job.JobPosting;
import com.careerops.backend.job.JobPostingRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class ApplicationStageRepositoryTest {

    @Autowired ApplicationStageRepository repository;
    @Autowired JobApplicationRepository jobApplicationRepository;
    @Autowired JobPostingRepository jobPostingRepository;
    @Autowired TestEntityManager entityManager;

    @Test
    void findsStagesSortedBySortOrder() {
        JobApplication application = saveApplication();
        ApplicationStage third = saveStage(application, StageType.INTERVIEW, 3);
        ApplicationStage first = saveStage(application, StageType.DOCUMENT, 1);

        assertThat(repository.findByJobApplicationIdOrderBySortOrderAsc(application.getId()))
                .extracting(ApplicationStage::getId)
                .containsExactly(first.getId(), third.getId());
    }

    @Test
    void findsStageOnlyWhenItBelongsToApplication() {
        JobApplication owner = saveApplication();
        JobApplication other = saveApplication();
        ApplicationStage stage = saveStage(owner, StageType.CODING_TEST, 1);

        assertThat(repository.findByIdAndJobApplicationId(stage.getId(), owner.getId())).isPresent();
        assertThat(repository.findByIdAndJobApplicationId(stage.getId(), other.getId())).isEmpty();
    }

    @Test
    void deletingApplicationCascadesToStages() {
        JobApplication application = saveApplication();
        saveStage(application, StageType.DOCUMENT, 1);
        saveStage(application, StageType.INTERVIEW, 2);

        entityManager.flush();
        entityManager.clear();

        jobApplicationRepository.deleteById(application.getId());
        jobApplicationRepository.flush();

        assertThat(repository.findByJobApplicationIdOrderBySortOrderAsc(application.getId())).isEmpty();
    }

    private ApplicationStage saveStage(JobApplication application, StageType type, int sortOrder) {
        return repository.saveAndFlush(new ApplicationStage(
                application, type, null, sortOrder, null, StageResult.PENDING, null));
    }

    private JobApplication saveApplication() {
        JobPosting posting = jobPostingRepository.saveAndFlush(new JobPosting(
                "기관", "공고", null, "신입", null, "OPEN", null, "정보기술", null,
                null, LocalDate.of(2026, 8, 31), "MANUAL", null,
                java.util.UUID.randomUUID().toString()));
        return jobApplicationRepository.saveAndFlush(
                new JobApplication(posting, ApplicationStatus.INTERESTED, null, null));
    }
}
