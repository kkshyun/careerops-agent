package com.careerops.backend.career;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import java.util.ArrayList;
import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class CareerExperienceSearchNonTransactionalTest {
    @Autowired MockMvc mockMvc;
    @Autowired CareerExperienceRepository repository;

    private final List<Long> createdIds = new ArrayList<>();

    @BeforeEach
    void setUp() {
        createdIds.add(repository.saveAndFlush(experience(ExperienceType.PROJECT, "Spring Boot 프로젝트")).getId());
        createdIds.add(repository.saveAndFlush(experience(ExperienceType.WORK, "Java 운영 자동화")).getId());
    }

    @AfterEach
    void cleanUp() {
        repository.deleteAllById(createdIds);
        repository.flush();
    }

    @Test
    void listsAllExperiencesWithoutParameters() throws Exception {
        mockMvc.perform(get("/api/career/experiences"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.totalElements").isNumber())
                .andExpect(jsonPath("$.totalPages").isNumber())
                .andExpect(jsonPath("$.page").isNumber())
                .andExpect(jsonPath("$.size").isNumber())
                .andExpect(jsonPath("$.content[?(@.title == 'Spring Boot 프로젝트')]").exists())
                .andExpect(jsonPath("$.content[0].bullets").doesNotExist())
                .andExpect(jsonPath("$.content[0].tags").doesNotExist());
    }

    @Test
    void filtersByTypeWithoutKeyword() throws Exception {
        mockMvc.perform(get("/api/career/experiences").param("type", "PROJECT"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].title").value("Spring Boot 프로젝트"));
    }

    @Test
    void filtersByKeywordWithoutType() throws Exception {
        mockMvc.perform(get("/api/career/experiences").param("keyword", "spring"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].title").value("Spring Boot 프로젝트"));
    }

    @Test
    void filtersByTypeAndKeyword() throws Exception {
        mockMvc.perform(get("/api/career/experiences")
                        .param("type", "PROJECT")
                        .param("keyword", "spring"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].type").value("PROJECT"));
    }

    @Test
    void filtersByKeywordIgnoringCase() throws Exception {
        mockMvc.perform(get("/api/career/experiences").param("keyword", "SPRING"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].title").value("Spring Boot 프로젝트"));
    }

    @Test
    void clampsRequestedPageSizeToOneHundred() throws Exception {
        mockMvc.perform(get("/api/career/experiences").param("size", "1000"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.size").value(100));
    }

    private static CareerExperience experience(ExperienceType type, String title) {
        return new CareerExperience(type, title, null, null, null, null, null, null);
    }
}
