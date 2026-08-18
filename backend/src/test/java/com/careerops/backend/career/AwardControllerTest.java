package com.careerops.backend.career;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.Rollback;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@Rollback
class AwardControllerTest {
    @Autowired MockMvc mockMvc;
    @Autowired AwardRepository repository;

    @Test
    void createsMinimalAndFullAward() throws Exception {
        mockMvc.perform(post("/api/career/awards").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"대상\"}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.title").value("대상"))
                .andExpect(jsonPath("$.issuer").isEmpty()).andExpect(jsonPath("$.awardedDate").isEmpty())
                .andExpect(jsonPath("$.description").isEmpty());

        mockMvc.perform(post("/api/career/awards").contentType(MediaType.APPLICATION_JSON).content("""
                {"title":"최우수상","issuer":"한국정보산업연합회","awardedDate":"2025-11-20",
                 "description":"프로젝트 경진대회 수상"}
                """))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.title").value("최우수상"))
                .andExpect(jsonPath("$.issuer").value("한국정보산업연합회"))
                .andExpect(jsonPath("$.awardedDate").value("2025-11-20"))
                .andExpect(jsonPath("$.description").value("프로젝트 경진대회 수상"));
    }

    @Test
    void rejectsMissingTitleWithoutCreatingRows() throws Exception {
        long before = repository.count();
        mockMvc.perform(post("/api/career/awards").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
        assertThat(repository.count()).isEqualTo(before);
    }

    @Test
    void listsWithPaginationClampDefaultSizeAndFixedOrdering() throws Exception {
        repository.save(award("날짜 없음", null));
        repository.save(award("과거", LocalDate.of(2024, 1, 1)));
        repository.saveAndFlush(award("최근", LocalDate.of(2026, 1, 1)));

        mockMvc.perform(get("/api/career/awards"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.content[0].title").value("최근"))
                .andExpect(jsonPath("$.content[1].title").value("과거"))
                .andExpect(jsonPath("$.content[2].title").value("날짜 없음"));
        mockMvc.perform(get("/api/career/awards").param("size", "1000"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.size").value(100));
        mockMvc.perform(get("/api/career/awards").param("page", "1").param("size", "2"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].title").value("날짜 없음"));
    }

    @Test
    void getsExistingAwardAndReturnsNotFoundForMissingOne() throws Exception {
        Award entity = repository.saveAndFlush(award("조회 대상", null));
        mockMvc.perform(get("/api/career/awards/{id}", entity.getId()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.title").value("조회 대상"));
        mockMvc.perform(get("/api/career/awards/{id}", Long.MAX_VALUE))
                .andExpect(status().isNotFound());
    }

    @Test
    void patchesOnlyProvidedFieldsAndReturnsNotFoundForMissingOne() throws Exception {
        Award entity = repository.saveAndFlush(new Award("기존 상", "기존 기관",
                LocalDate.of(2025, 2, 1), "기존 설명"));

        mockMvc.perform(patch("/api/career/awards/{id}", entity.getId())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"description\":\"변경 설명\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.title").value("기존 상"))
                .andExpect(jsonPath("$.issuer").value("기존 기관"))
                .andExpect(jsonPath("$.awardedDate").value("2025-02-01"))
                .andExpect(jsonPath("$.description").value("변경 설명"));
        mockMvc.perform(patch("/api/career/awards/{id}", Long.MAX_VALUE)
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void deletesExistingAwardAndReturnsNotFoundAfterward() throws Exception {
        Award entity = repository.saveAndFlush(award("삭제 대상", null));
        mockMvc.perform(delete("/api/career/awards/{id}", entity.getId()))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/career/awards/{id}", entity.getId()))
                .andExpect(status().isNotFound());
        mockMvc.perform(delete("/api/career/awards/{id}", Long.MAX_VALUE))
                .andExpect(status().isNotFound());
    }

    private Award award(String title, LocalDate awardedDate) {
        return new Award(title, null, awardedDate, null);
    }
}
