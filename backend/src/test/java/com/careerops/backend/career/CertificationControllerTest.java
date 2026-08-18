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
class CertificationControllerTest {
    @Autowired MockMvc mockMvc;
    @Autowired CertificationRepository repository;

    @Test
    void createsMinimalAndFullCertification() throws Exception {
        mockMvc.perform(post("/api/career/certifications").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"정보처리기사\"}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.name").value("정보처리기사"))
                .andExpect(jsonPath("$.issuer").isEmpty()).andExpect(jsonPath("$.acquiredDate").isEmpty())
                .andExpect(jsonPath("$.expirationDate").isEmpty()).andExpect(jsonPath("$.credentialId").isEmpty())
                .andExpect(jsonPath("$.description").isEmpty());

        mockMvc.perform(post("/api/career/certifications").contentType(MediaType.APPLICATION_JSON).content("""
                {"name":"AWS Solutions Architect","issuer":"AWS","acquiredDate":"2025-01-10",
                 "expirationDate":"2028-01-10","credentialId":"ABC-123","description":"클라우드 자격"}
                """))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.name").value("AWS Solutions Architect"))
                .andExpect(jsonPath("$.issuer").value("AWS"))
                .andExpect(jsonPath("$.acquiredDate").value("2025-01-10"))
                .andExpect(jsonPath("$.expirationDate").value("2028-01-10"))
                .andExpect(jsonPath("$.credentialId").value("ABC-123"))
                .andExpect(jsonPath("$.description").value("클라우드 자격"));
    }

    @Test
    void rejectsMissingNameAndReversedDatesWithoutCreatingRows() throws Exception {
        long before = repository.count();
        mockMvc.perform(post("/api/career/certifications").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/career/certifications").contentType(MediaType.APPLICATION_JSON).content("""
                {"name":"날짜 오류","acquiredDate":"2026-02-01","expirationDate":"2026-01-01"}
                """))
                .andExpect(status().isBadRequest());
        assertThat(repository.count()).isEqualTo(before);
    }

    @Test
    void listsWithPaginationClampDefaultSizeAndFixedOrdering() throws Exception {
        repository.save(new Certification("날짜 없음", null, null, null, null, null));
        repository.save(new Certification("과거", null, LocalDate.of(2024, 1, 1), null, null, null));
        repository.saveAndFlush(new Certification("최근", null, LocalDate.of(2026, 1, 1), null, null, null));

        mockMvc.perform(get("/api/career/certifications"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.content[0].name").value("최근"))
                .andExpect(jsonPath("$.content[1].name").value("과거"))
                .andExpect(jsonPath("$.content[2].name").value("날짜 없음"));
        mockMvc.perform(get("/api/career/certifications").param("size", "1000"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.size").value(100));
        mockMvc.perform(get("/api/career/certifications").param("page", "1").param("size", "2"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].name").value("날짜 없음"));
    }

    @Test
    void getsExistingCertificationAndReturnsNotFoundForMissingOne() throws Exception {
        Certification entity = repository.saveAndFlush(new Certification("SQLD", null, null, null, null, null));
        mockMvc.perform(get("/api/career/certifications/{id}", entity.getId()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.name").value("SQLD"));
        mockMvc.perform(get("/api/career/certifications/{id}", Long.MAX_VALUE))
                .andExpect(status().isNotFound());
    }

    @Test
    void patchesOnlyProvidedFieldsAndValidatesMergedDates() throws Exception {
        Certification entity = repository.saveAndFlush(new Certification("기존", "발급처",
                LocalDate.of(2025, 2, 1), LocalDate.of(2027, 2, 1), "ID-1", "기존 설명"));

        mockMvc.perform(patch("/api/career/certifications/{id}", entity.getId())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"description\":\"변경 설명\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.name").value("기존"))
                .andExpect(jsonPath("$.issuer").value("발급처"))
                .andExpect(jsonPath("$.credentialId").value("ID-1"))
                .andExpect(jsonPath("$.description").value("변경 설명"));
        mockMvc.perform(patch("/api/career/certifications/{id}", entity.getId())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"expirationDate\":\"2025-01-31\"}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(patch("/api/career/certifications/{id}", Long.MAX_VALUE)
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void deletesExistingCertificationAndReturnsNotFoundAfterward() throws Exception {
        Certification entity = repository.saveAndFlush(new Certification("삭제", null, null, null, null, null));
        mockMvc.perform(delete("/api/career/certifications/{id}", entity.getId()))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/career/certifications/{id}", entity.getId()))
                .andExpect(status().isNotFound());
        mockMvc.perform(delete("/api/career/certifications/{id}", Long.MAX_VALUE))
                .andExpect(status().isNotFound());
    }
}
