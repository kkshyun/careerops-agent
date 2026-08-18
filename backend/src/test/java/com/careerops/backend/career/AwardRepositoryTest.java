package com.careerops.backend.career;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.data.domain.PageRequest;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class AwardRepositoryTest {
    @Autowired AwardRepository repository;

    @Test
    void savesAndFindsAwardWithNullableFields() {
        Award saved = repository.saveAndFlush(award("대상", null));

        Award found = repository.findById(saved.getId()).orElseThrow();
        assertThat(found.getTitle()).isEqualTo("대상");
        assertThat(found.getIssuer()).isNull();
        assertThat(found.getAwardedDate()).isNull();
        assertThat(found.getDescription()).isNull();
        assertThat(found.getCreatedAt()).isNotNull();
        assertThat(found.getUpdatedAt()).isNotNull();
    }

    @Test
    void ordersByAwardedDateDescendingWithNullsLast() {
        repository.save(award("날짜 없음", null));
        repository.save(award("과거", LocalDate.of(2024, 1, 1)));
        repository.saveAndFlush(award("최근", LocalDate.of(2026, 1, 1)));

        assertThat(repository.findAllOrderByAwardedDateDescNullsLast(PageRequest.of(0, 10)).getContent())
                .extracting(Award::getTitle).containsExactly("최근", "과거", "날짜 없음");
    }

    private Award award(String title, LocalDate awardedDate) {
        return new Award(title, null, awardedDate, null);
    }
}
