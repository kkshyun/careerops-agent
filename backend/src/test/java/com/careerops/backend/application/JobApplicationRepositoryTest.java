package com.careerops.backend.application;

import com.careerops.backend.application.dto.JobApplicationResponse;
import com.careerops.backend.job.JobPosting;
import com.careerops.backend.job.JobPostingRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class JobApplicationRepositoryTest {

    @Autowired
    private JobApplicationRepository repository;

    @Autowired
    private JobPostingRepository jobPostingRepository;

    @Test
    void checksExistenceByJobPostingId() {
        JobPosting posting = savePosting("기관", "공고", "OPEN");

        assertThat(repository.existsByJobPostingId(posting.getId())).isFalse();
        repository.saveAndFlush(new JobApplication(posting, ApplicationStatus.INTERESTED, null, null));

        assertThat(repository.existsByJobPostingId(posting.getId())).isTrue();
    }

    @Test
    void mapsJoinedJobPostingFieldsToResponse() {
        JobPosting posting = savePosting("한국전력공사", "신입 IT 개발자", "OPEN");
        JobApplication application = repository.saveAndFlush(new JobApplication(
                posting, ApplicationStatus.SUBMITTED, "제출 완료", LocalDate.of(2026, 8, 18)));

        JobApplicationResponse response = repository.findResponseById(application.getId()).orElseThrow();

        assertThat(response.id()).isEqualTo(application.getId());
        assertThat(response.status()).isEqualTo(ApplicationStatus.SUBMITTED);
        assertThat(response.memo()).isEqualTo("제출 완료");
        assertThat(response.appliedAt()).isEqualTo(LocalDate.of(2026, 8, 18));
        assertThat(response.createdAt()).isNotNull();
        assertThat(response.updatedAt()).isNotNull();
        assertThat(response.jobPostingId()).isEqualTo(posting.getId());
        assertThat(response.companyName()).isEqualTo("한국전력공사");
        assertThat(response.title()).isEqualTo("신입 IT 개발자");
        assertThat(response.applicationEndAt()).isEqualTo(LocalDate.of(2026, 8, 31));
        assertThat(response.jobPostingStatus()).isEqualTo("OPEN");
    }

    @Test
    void filtersByStatusAndSortsByUpdatedAtDescending() throws InterruptedException {
        JobApplication older = repository.saveAndFlush(new JobApplication(
                savePosting("기관1", "공고1", "OPEN"), ApplicationStatus.SUBMITTED, null, null));
        Thread.sleep(5);
        JobApplication newer = repository.saveAndFlush(new JobApplication(
                savePosting("기관2", "공고2", "OPEN"), ApplicationStatus.SUBMITTED, null, null));
        repository.saveAndFlush(new JobApplication(
                savePosting("기관3", "공고3", "OPEN"), ApplicationStatus.PLANNED, null, null));

        Page<JobApplicationResponse> result = repository.search(
                ApplicationStatus.SUBMITTED, PageRequest.of(0, 20));

        assertThat(result.getContent()).extracting(JobApplicationResponse::id)
                .containsExactly(newer.getId(), older.getId());
    }

    @Test
    void paginatesSearchResults() {
        for (int index = 0; index < 21; index++) {
            JobPosting posting = savePosting("기관" + index, "공고" + index, "OPEN");
            repository.save(new JobApplication(posting, ApplicationStatus.INTERESTED, null, null));
        }
        repository.flush();

        Page<JobApplicationResponse> firstPage = repository.search(null, PageRequest.of(0, 20));
        Page<JobApplicationResponse> secondPage = repository.search(null, PageRequest.of(1, 20));

        assertThat(firstPage.getContent()).hasSize(20);
        assertThat(firstPage.getTotalElements()).isEqualTo(21);
        assertThat(firstPage.getTotalPages()).isEqualTo(2);
        assertThat(secondPage.getContent()).hasSize(1);
    }

    private JobPosting savePosting(String companyName, String title, String status) {
        return jobPostingRepository.saveAndFlush(new JobPosting(
                companyName, title, null, "신입", null, status, null, "정보기술", null,
                null, LocalDate.of(2026, 8, 31), "MANUAL", null,
                java.util.UUID.randomUUID().toString()));
    }
}
