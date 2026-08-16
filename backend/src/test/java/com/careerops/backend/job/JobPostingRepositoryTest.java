package com.careerops.backend.job;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import java.time.LocalDate;
import org.springframework.dao.DataIntegrityViolationException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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

    @Test
    void rejectsDuplicateSourceAndExternalId() {
        repository.saveAndFlush(new JobPosting(
                "기관1", "공고1", null, null, null, null, null, null, null,
                null, null, "ALIO", null, "unique-constraint-test"
        ));

        assertThatThrownBy(() -> repository.saveAndFlush(new JobPosting(
                "기관2", "공고2", null, null, null, null, null, null, null,
                null, null, "ALIO", null, "unique-constraint-test"
        ))).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void searchesByEachFilterAndCombinesFiltersWithAnd() {
        JobPosting matching = save("한국전력공사", "OPEN", "신입+경력", "정보기술,경영·행정·사무",
                LocalDate.of(2026, 8, 20));
        JobPosting closed = save("한국전력공사", "CLOSED", "신입", "정보기술",
                LocalDate.of(2026, 8, 21));
        save("다른기관", null, "경력", "회계", LocalDate.of(2026, 8, 22));

        assertThat(search("OPEN", null, null, null).getContent()).containsExactly(matching);
        assertThat(search(null, "신입", null, null).getContent()).containsExactly(matching, closed);
        assertThat(search(null, null, "한국전력", null).getContent()).hasSize(2);
        assertThat(search(null, null, null, "정보기술").getContent()).hasSize(2);
        assertThat(search("OPEN", "신입", null, null).getContent()).containsExactly(matching);
    }

    @Test
    void sortsByApplicationEndAtAscendingWithNullLast() {
        JobPosting latest = save("기관3", "OPEN", "신입", "정보기술", LocalDate.of(2026, 8, 30));
        JobPosting noDeadline = save("기관-null", "OPEN", "신입", "정보기술", null);
        JobPosting earliest = save("기관1", "OPEN", "신입", "정보기술", LocalDate.of(2026, 8, 10));

        assertThat(search(null, null, null, null).getContent())
                .containsExactly(earliest, latest, noDeadline);
    }

    @Test
    void paginatesSearchResults() {
        for (int index = 0; index < 21; index++) {
            save("기관" + index, "OPEN", "신입", "정보기술", LocalDate.of(2026, 8, 1).plusDays(index));
        }

        Page<JobPosting> firstPage = repository.search(null, null, null, null, PageRequest.of(0, 20));
        Page<JobPosting> secondPage = repository.search(null, null, null, null, PageRequest.of(1, 20));

        assertThat(firstPage.getContent()).hasSize(20);
        assertThat(firstPage.getTotalElements()).isEqualTo(21);
        assertThat(firstPage.getTotalPages()).isEqualTo(2);
        assertThat(secondPage.getContent()).hasSize(1);
    }

    private Page<JobPosting> search(String status, String careerLevel, String companyName, String jobCategory) {
        return repository.search(
                status, likePattern(careerLevel), likePattern(companyName), likePattern(jobCategory),
                PageRequest.of(0, 20));
    }

    private String likePattern(String value) {
        return value == null ? null : "%" + value + "%";
    }

    private JobPosting save(
            String companyName, String status, String careerLevel, String jobCategory, LocalDate applicationEndAt) {
        return repository.save(new JobPosting(
                companyName, "공고", null, careerLevel, null, status, null, jobCategory, null,
                null, applicationEndAt, "ALIO", null, java.util.UUID.randomUUID().toString()
        ));
    }
}
