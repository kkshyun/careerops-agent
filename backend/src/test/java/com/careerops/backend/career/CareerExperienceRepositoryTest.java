package com.careerops.backend.career;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class CareerExperienceRepositoryTest {
    @Autowired CareerExperienceRepository repository;
    @Autowired ExperienceBulletRepository bulletRepository;
    @Autowired ExperienceTagRepository tagRepository;
    @Autowired EntityManager entityManager;

    @Test
    void savesFiltersAndOrdersBullets() {
        CareerExperience project = repository.saveAndFlush(experience(ExperienceType.PROJECT, "추천 시스템"));
        repository.saveAndFlush(experience(ExperienceType.WORK, "운영 자동화"));
        bulletRepository.save(new ExperienceBullet(project, BulletType.RESULT, "결과", 2));
        bulletRepository.saveAndFlush(new ExperienceBullet(project, BulletType.CONTEXT, "배경", 0));

        assertThat(repository.findByType(ExperienceType.PROJECT, PageRequest.of(0, 10)).getContent())
                .extracting(CareerExperience::getTitle).containsExactly("추천 시스템");
        assertThat(bulletRepository.findByCareerExperienceIdOrderBySortOrderAsc(project.getId()))
                .extracting(ExperienceBullet::getSortOrder).containsExactly(0, 2);
    }

    @Test
    void tagUniqueIndexIgnoresCase() {
        CareerExperience experience = repository.saveAndFlush(experience(ExperienceType.PROJECT, "프로젝트"));
        tagRepository.saveAndFlush(new ExperienceTag(experience, "Spring"));
        assertThatThrownBy(() -> tagRepository.saveAndFlush(new ExperienceTag(experience, "spring")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void deletingExperienceCascadesToChildren() {
        CareerExperience experience = repository.saveAndFlush(experience(ExperienceType.ACTIVITY, "활동"));
        bulletRepository.saveAndFlush(new ExperienceBullet(experience, BulletType.ACTION, "실행", 0));
        tagRepository.saveAndFlush(new ExperienceTag(experience, "Java"));
        entityManager.clear();

        repository.deleteById(experience.getId());
        repository.flush();
        entityManager.clear();

        assertThat(bulletRepository.countByCareerExperienceId(experience.getId())).isZero();
        assertThat(tagRepository.countByCareerExperienceId(experience.getId())).isZero();
    }

    private CareerExperience experience(ExperienceType type, String title) {
        return new CareerExperience(type, title, null, null, null, null, null, null);
    }
}
