package com.careerops.backend.career;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.Rollback;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@Rollback
class CareerExperienceControllerTest {
    @Autowired MockMvc mockMvc;
    @Autowired CareerExperienceRepository repository;
    @Autowired ExperienceBulletRepository bulletRepository;
    @Autowired ExperienceTagRepository tagRepository;
    @Autowired EntityManager entityManager;

    @Test
    void createsMinimalAndFullExperienceWithOrderedBulletsAndDeduplicatedTags() throws Exception {
        mockMvc.perform(post("/api/career/experiences").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"PROJECT\",\"title\":\"최소 경험\"}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.bullets").isEmpty())
                .andExpect(jsonPath("$.tags").isEmpty());

        mockMvc.perform(post("/api/career/experiences").contentType(MediaType.APPLICATION_JSON).content("""
                {"type":"WORK","title":"추천 고도화","organization":"회사","role":"개발",
                 "startDate":"2025-03-01","endDate":"2025-08-31","summary":"성과","detail":"상세",
                 "bullets":[{"bulletType":"CONTEXT","content":"배경"},{"bulletType":"ACTION","content":"실행"},{"bulletType":"RESULT","content":"결과"}],
                 "tags":["Spring","spring","Redis"]}
                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.bullets[0].sortOrder").value(0))
                .andExpect(jsonPath("$.bullets[2].sortOrder").value(2))
                .andExpect(jsonPath("$.tags.length()").value(2));
    }

    @Test
    void rejectsInvalidRequestsAtomically() throws Exception {
        long before = repository.count();
        mockMvc.perform(post("/api/career/experiences").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"PROJECT\"}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/career/experiences").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"PROJECT\",\"title\":\"날짜\",\"startDate\":\"2026-02-01\",\"endDate\":\"2026-01-01\"}"))
                .andExpect(status().isBadRequest());
        assertThat(repository.count()).isEqualTo(before);
        assertThat(bulletRepository.count()).isZero();
        assertThat(tagRepository.count()).isZero();
    }

    @Test
    void listsWithCombinedFiltersAsFlatResponses() throws Exception {
        repository.save(new CareerExperience(ExperienceType.PROJECT, "추천 시스템", null, null, null, null, null, null));
        repository.saveAndFlush(new CareerExperience(ExperienceType.WORK, "추천 운영", null, null, null, null, null, null));
        mockMvc.perform(get("/api/career/experiences").param("type", "PROJECT").param("keyword", "시스템"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].type").value("PROJECT"))
                .andExpect(jsonPath("$.content[0].bullets").doesNotExist())
                .andExpect(jsonPath("$.content[0].tags").doesNotExist());
        mockMvc.perform(get("/api/career/experiences").param("type", "WORK"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].title").value("추천 운영"));
        mockMvc.perform(get("/api/career/experiences").param("keyword", "시스템"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].title").value("추천 시스템"));
        mockMvc.perform(get("/api/career/experiences").param("size", "1000"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.size").value(100));
    }

    @Test
    void getsDetailAndPatchesListsUsingWholeReplacementRules() throws Exception {
        CareerExperience entity = repository.saveAndFlush(new CareerExperience(ExperienceType.PROJECT, "기존", null, null, null, null, "기존 요약", null));
        bulletRepository.saveAndFlush(new ExperienceBullet(entity, BulletType.ACTION, "기존 불릿", 0));
        tagRepository.saveAndFlush(new ExperienceTag(entity, "Java"));

        mockMvc.perform(patch("/api/career/experiences/{id}", entity.getId()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"summary\":\"변경\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.title").value("기존"))
                .andExpect(jsonPath("$.bullets[0].content").value("기존 불릿"))
                .andExpect(jsonPath("$.tags[0]").value("Java"));
        mockMvc.perform(patch("/api/career/experiences/{id}", entity.getId()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"bullets\":[],\"tags\":[\"Kotlin\"]}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.bullets").isEmpty())
                .andExpect(jsonPath("$.tags[0]").value("Kotlin"));
        mockMvc.perform(patch("/api/career/experiences/{id}", entity.getId()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"bullets\":[{\"bulletType\":\"RESULT\",\"content\":\"새 결과\"}],\"tags\":null}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.bullets[0].sortOrder").value(0))
                .andExpect(jsonPath("$.tags[0]").value("Kotlin"));
        mockMvc.perform(patch("/api/career/experiences/{id}", entity.getId()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tags\":[\"PostgreSQL\"]}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.bullets[0].content").value("새 결과"))
                .andExpect(jsonPath("$.tags[0]").value("PostgreSQL"));
        mockMvc.perform(get("/api/career/experiences/{id}", entity.getId()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.bullets[0].content").value("새 결과"))
                .andExpect(jsonPath("$.tags[0]").value("PostgreSQL"));
    }

    @Test
    void returnsNotFoundAndDeletesWithDatabaseCascade() throws Exception {
        CareerExperience entity = repository.saveAndFlush(new CareerExperience(ExperienceType.OTHER, "삭제", null, null, null, null, null, null));
        bulletRepository.saveAndFlush(new ExperienceBullet(entity, null, "불릿", 0));
        tagRepository.saveAndFlush(new ExperienceTag(entity, "태그"));
        entityManager.clear();
        mockMvc.perform(delete("/api/career/experiences/{id}", entity.getId())).andExpect(status().isNoContent());
        assertThat(bulletRepository.countByCareerExperienceId(entity.getId())).isZero();
        assertThat(tagRepository.countByCareerExperienceId(entity.getId())).isZero();
        mockMvc.perform(get("/api/career/experiences/{id}", entity.getId())).andExpect(status().isNotFound());
        mockMvc.perform(patch("/api/career/experiences/{id}", Long.MAX_VALUE).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isNotFound());
        mockMvc.perform(delete("/api/career/experiences/{id}", Long.MAX_VALUE)).andExpect(status().isNotFound());
    }
}
