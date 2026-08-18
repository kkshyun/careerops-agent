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
class CertificationRepositoryTest {
    @Autowired CertificationRepository repository;

    @Test
    void savesAndFindsCertificationWithNullableFields() {
        Certification saved = repository.saveAndFlush(certification("정보처리기사", null));

        Certification found = repository.findById(saved.getId()).orElseThrow();
        assertThat(found.getName()).isEqualTo("정보처리기사");
        assertThat(found.getIssuer()).isNull();
        assertThat(found.getAcquiredDate()).isNull();
        assertThat(found.getExpirationDate()).isNull();
        assertThat(found.getCredentialId()).isNull();
        assertThat(found.getDescription()).isNull();
        assertThat(found.getCreatedAt()).isNotNull();
        assertThat(found.getUpdatedAt()).isNotNull();
    }

    @Test
    void ordersByAcquiredDateDescendingWithNullsLast() {
        repository.save(certification("날짜 없음", null));
        repository.save(certification("과거", LocalDate.of(2024, 1, 1)));
        repository.saveAndFlush(certification("최근", LocalDate.of(2026, 1, 1)));

        assertThat(repository.findAllOrderByAcquiredDateDescNullsLast(PageRequest.of(0, 10)).getContent())
                .extracting(Certification::getName).containsExactly("최근", "과거", "날짜 없음");
    }

    private Certification certification(String name, LocalDate acquiredDate) {
        return new Certification(name, null, acquiredDate, null, null, null);
    }
}
