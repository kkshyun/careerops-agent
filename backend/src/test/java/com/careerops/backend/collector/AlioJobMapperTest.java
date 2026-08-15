package com.careerops.backend.collector;

import com.careerops.backend.collector.alio.AlioJobItem;
import com.careerops.backend.collector.alio.AlioJobMapper;
import com.careerops.backend.job.dto.JobPostingCreateRequest;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class AlioJobMapperTest {

    @Test
    void mapsAlioFieldsAndParsesDates() {
        AlioJobItem item = new AlioJobItem(
                "기관", "공고", "https://example.invalid/job/1", 123L,
                "20260801", "20260831", "신입", "정규직", "학력무관", "Y", "C0059",
                "정보기술", "서울"
        );

        JobPostingCreateRequest request = AlioJobMapper.from(item);

        assertThat(request.companyName()).isEqualTo("기관");
        assertThat(request.title()).isEqualTo("공고");
        assertThat(request.source()).isEqualTo("ALIO");
        assertThat(request.sourceUrl()).isEqualTo("https://example.invalid/job/1");
        assertThat(request.externalId()).isEqualTo("123");
        assertThat(request.employmentType()).isEqualTo("정규직");
        assertThat(request.careerLevel()).isEqualTo("신입");
        assertThat(request.educationRequirement()).isEqualTo("학력무관");
        assertThat(request.status()).isEqualTo("OPEN");
        assertThat(request.institutionCode()).isEqualTo("C0059");
        assertThat(request.applicationStartAt()).isEqualTo(LocalDate.of(2026, 8, 1));
        assertThat(request.applicationEndAt()).isEqualTo(LocalDate.of(2026, 8, 31));
    }

    @Test
    void mapsMissingOptionalAndInvalidDateFieldsToNull() {
        AlioJobItem item = new AlioJobItem("기관", "공고", "not-a-url", null,
                "invalid", null, "", null, null, null, null, null, " ");

        JobPostingCreateRequest request = AlioJobMapper.from(item);

        assertThat(request.employmentType()).isNull();
        assertThat(request.careerLevel()).isNull();
        assertThat(request.educationRequirement()).isNull();
        assertThat(request.status()).isNull();
        assertThat(request.institutionCode()).isNull();
        assertThat(request.jobCategory()).isNull();
        assertThat(request.location()).isNull();
        assertThat(request.sourceUrl()).isNull();
        assertThat(request.externalId()).isNull();
        assertThat(request.applicationStartAt()).isNull();
        assertThat(request.applicationEndAt()).isNull();
    }

    @Test
    void mapsClosedAndUnknownStatuses() {
        AlioJobItem closed = new AlioJobItem("기관", "공고", null, 1L, null, null,
                null, null, null, "N", null, null, null);
        AlioJobItem unknown = new AlioJobItem("기관", "공고", null, 2L, null, null,
                null, null, null, "UNKNOWN", null, null, null);

        assertThat(AlioJobMapper.from(closed).status()).isEqualTo("CLOSED");
        assertThat(AlioJobMapper.from(unknown).status()).isNull();
    }
}
