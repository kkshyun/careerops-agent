package com.careerops.backend.career;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.data.domain.PageRequest;
import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class EducationRepositoryTest {
    @Autowired EducationRepository repository;

    @Test
    void savesAndFindsEducationWithGpaAndNullableFields() {
        Education saved = repository.saveAndFlush(new Education("한국대학교", null, null, null,
                null, null, new BigDecimal("3.80"), new BigDecimal("4.50"), null));

        Education found = repository.findById(saved.getId()).orElseThrow();
        assertThat(found.getInstitution()).isEqualTo("한국대학교");
        assertThat(found.getMajor()).isNull();
        assertThat(found.getDegree()).isNull();
        assertThat(found.getStatus()).isNull();
        assertThat(found.getStartDate()).isNull();
        assertThat(found.getEndDate()).isNull();
        assertThat(found.getGpa()).isEqualByComparingTo("3.80");
        assertThat(found.getGpaScale()).isEqualByComparingTo("4.50");
        assertThat(found.getDescription()).isNull();
        assertThat(found.getCreatedAt()).isNotNull();
        assertThat(found.getUpdatedAt()).isNotNull();
    }

    @Test
    void ordersByStartDateDescendingWithNullsLast() {
        repository.save(education("날짜 없음", null));
        repository.save(education("과거", LocalDate.of(2022, 3, 1)));
        repository.saveAndFlush(education("최근", LocalDate.of(2025, 3, 1)));

        assertThat(repository.findAllOrderByStartDateDescNullsLast(PageRequest.of(0, 10)).getContent())
                .extracting(Education::getInstitution).containsExactly("최근", "과거", "날짜 없음");
    }

    private Education education(String institution, LocalDate startDate) {
        return new Education(institution, null, null, null, startDate, null, null, null, null);
    }
}
