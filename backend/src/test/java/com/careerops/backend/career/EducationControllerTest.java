package com.careerops.backend.career;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.Rollback;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@Rollback
class EducationControllerTest {
    @Autowired MockMvc mockMvc;
    @Autowired EducationRepository repository;

    @Test
    void createsMinimalAndFullEducation() throws Exception {
        mockMvc.perform(post("/api/career/educations").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"institution\":\"한국대학교\"}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.institution").value("한국대학교"))
                .andExpect(jsonPath("$.major").isEmpty()).andExpect(jsonPath("$.degree").isEmpty())
                .andExpect(jsonPath("$.status").isEmpty()).andExpect(jsonPath("$.startDate").isEmpty())
                .andExpect(jsonPath("$.endDate").isEmpty()).andExpect(jsonPath("$.gpa").isEmpty())
                .andExpect(jsonPath("$.gpaScale").isEmpty()).andExpect(jsonPath("$.description").isEmpty());

        mockMvc.perform(post("/api/career/educations").contentType(MediaType.APPLICATION_JSON).content("""
                {"institution":"서울대학교","major":"컴퓨터공학","degree":"BACHELOR","status":"GRADUATED",
                 "startDate":"2021-03-01","endDate":"2025-02-28","gpa":3.80,"gpaScale":4.50,
                 "description":"학부 과정"}
                """))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.institution").value("서울대학교"))
                .andExpect(jsonPath("$.major").value("컴퓨터공학"))
                .andExpect(jsonPath("$.degree").value("BACHELOR"))
                .andExpect(jsonPath("$.status").value("GRADUATED"))
                .andExpect(jsonPath("$.startDate").value("2021-03-01"))
                .andExpect(jsonPath("$.endDate").value("2025-02-28"))
                .andExpect(jsonPath("$.gpa").value(3.8)).andExpect(jsonPath("$.gpaScale").value(4.5))
                .andExpect(jsonPath("$.description").value("학부 과정"));
    }

    @Test
    void rejectsInvalidCreateRequestsWithoutCreatingRows() throws Exception {
        long before = repository.count();
        String[] invalidRequests = {
                "{}",
                "{\"institution\":\"날짜 오류\",\"startDate\":\"2025-03-01\",\"endDate\":\"2025-02-28\"}",
                "{\"institution\":\"GPA만\",\"gpa\":3.8}",
                "{\"institution\":\"척도만\",\"gpaScale\":4.5}",
                "{\"institution\":\"GPA 초과\",\"gpa\":4.6,\"gpaScale\":4.5}",
                "{\"institution\":\"잘못된 학위\",\"degree\":\"DIPLOMA\"}"
        };
        for (String request : invalidRequests) {
            mockMvc.perform(post("/api/career/educations").contentType(MediaType.APPLICATION_JSON).content(request))
                    .andExpect(status().isBadRequest());
        }
        assertThat(repository.count()).isEqualTo(before);
    }

    @Test
    void acceptsValidGpaWithoutPrecisionLoss() throws Exception {
        mockMvc.perform(post("/api/career/educations").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"institution\":\"GPA 학교\",\"gpa\":3.80,\"gpaScale\":4.50}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.gpa").value(3.8))
                .andExpect(jsonPath("$.gpaScale").value(4.5));
        Education saved = repository.findAll().stream()
                .filter(item -> item.getInstitution().equals("GPA 학교")).findFirst().orElseThrow();
        assertThat(saved.getGpa()).isEqualByComparingTo("3.80");
        assertThat(saved.getGpaScale()).isEqualByComparingTo("4.50");
    }

    @Test
    void listsWithPaginationClampDefaultSizeAndFixedOrdering() throws Exception {
        repository.save(education("날짜 없음", null));
        repository.save(education("과거", LocalDate.of(2022, 3, 1)));
        repository.saveAndFlush(education("최근", LocalDate.of(2025, 3, 1)));

        mockMvc.perform(get("/api/career/educations"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.content[0].institution").value("최근"))
                .andExpect(jsonPath("$.content[1].institution").value("과거"))
                .andExpect(jsonPath("$.content[2].institution").value("날짜 없음"));
        mockMvc.perform(get("/api/career/educations").param("size", "1000"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.size").value(100));
        mockMvc.perform(get("/api/career/educations").param("page", "1").param("size", "2"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].institution").value("날짜 없음"));
    }

    @Test
    void getsExistingEducationAndReturnsNotFoundForMissingOne() throws Exception {
        Education entity = repository.saveAndFlush(education("조회 학교", null));
        mockMvc.perform(get("/api/career/educations/{id}", entity.getId()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.institution").value("조회 학교"));
        mockMvc.perform(get("/api/career/educations/{id}", Long.MAX_VALUE))
                .andExpect(status().isNotFound());
    }

    @Test
    void patchesOnlyProvidedFieldsAndValidatesMergedValues() throws Exception {
        Education entity = repository.saveAndFlush(new Education("기존 학교", "기존 전공",
                EducationDegree.BACHELOR, EducationStatus.GRADUATED, LocalDate.of(2021, 3, 1),
                LocalDate.of(2025, 2, 28), new BigDecimal("3.80"), new BigDecimal("4.50"), "기존 설명"));

        mockMvc.perform(patch("/api/career/educations/{id}", entity.getId())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"description\":\"변경 설명\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.institution").value("기존 학교"))
                .andExpect(jsonPath("$.major").value("기존 전공"))
                .andExpect(jsonPath("$.gpa").value(3.8)).andExpect(jsonPath("$.gpaScale").value(4.5))
                .andExpect(jsonPath("$.description").value("변경 설명"));
        mockMvc.perform(patch("/api/career/educations/{id}", entity.getId())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"gpa\":4.60}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(patch("/api/career/educations/{id}", entity.getId())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"endDate\":\"2021-02-28\"}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(patch("/api/career/educations/{id}", Long.MAX_VALUE)
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void deletesExistingEducationAndReturnsNotFoundAfterward() throws Exception {
        Education entity = repository.saveAndFlush(education("삭제 학교", null));
        mockMvc.perform(delete("/api/career/educations/{id}", entity.getId()))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/career/educations/{id}", entity.getId()))
                .andExpect(status().isNotFound());
        mockMvc.perform(delete("/api/career/educations/{id}", Long.MAX_VALUE))
                .andExpect(status().isNotFound());
    }

    private Education education(String institution, LocalDate startDate) {
        return new Education(institution, null, null, null, startDate, null, null, null, null);
    }
}
