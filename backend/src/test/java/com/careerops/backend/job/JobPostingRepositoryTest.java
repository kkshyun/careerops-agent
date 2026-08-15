package com.careerops.backend.job;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class JobPostingRepositoryTest {

    @Autowired
    private JobPostingRepository repository;

    @Test
    void savesAndFindsJobPostingById() {
        JobPosting saved = repository.saveAndFlush(new JobPosting(
                "CareerOps Bank",
                "신입 IT 개발자",
                "정규직",
                "신입",
                "대졸",
                "OPEN",
                "B001",
                "IT/전산",
                "서울",
                LocalDate.of(2026, 8, 13),
                LocalDate.of(2026, 8, 31),
                "MANUAL",
                "https://example.com/jobs/123",
                "123"
        ));

        JobPosting found = repository.findById(saved.getId()).orElseThrow();

        assertThat(found.getCompanyName()).isEqualTo("CareerOps Bank");
        assertThat(found.getTitle()).isEqualTo("신입 IT 개발자");
        assertThat(found.getEmploymentType()).isEqualTo("정규직");
        assertThat(found.getCareerLevel()).isEqualTo("신입");
        assertThat(found.getEducationRequirement()).isEqualTo("대졸");
        assertThat(found.getStatus()).isEqualTo("OPEN");
        assertThat(found.getInstitutionCode()).isEqualTo("B001");
        assertThat(found.getJobCategory()).isEqualTo("IT/전산");
        assertThat(found.getLocation()).isEqualTo("서울");
        assertThat(found.getApplicationStartAt()).isEqualTo(LocalDate.of(2026, 8, 13));
        assertThat(found.getApplicationEndAt()).isEqualTo(LocalDate.of(2026, 8, 31));
        assertThat(found.getSource()).isEqualTo("MANUAL");
        assertThat(found.getSourceUrl()).isEqualTo("https://example.com/jobs/123");
        assertThat(found.getExternalId()).isEqualTo("123");
        assertThat(found.getCreatedAt()).isNotNull();
    }

    @Test
    void returnsEmptyForUnknownId() {
        assertThat(repository.findById(Long.MAX_VALUE)).isEmpty();
    }

    @Test
    void findsBySourceAndExternalId() {
        JobPosting saved = repository.saveAndFlush(new JobPosting(
                "기관", "공고", null, null, null, null, null, null, null,
                null, null, "ALIO", null, "external-1"
        ));

        assertThat(repository.findFirstBySourceAndExternalId("ALIO", "external-1")).contains(saved);
        assertThat(repository.findFirstBySourceAndExternalId("ALIO", "missing")).isEmpty();
    }
}
