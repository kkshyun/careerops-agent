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
                "20260801", "20260831", "신입", "정보기술", "서울"
        );

        JobPostingCreateRequest request = AlioJobMapper.from(item);

        assertThat(request.companyName()).isEqualTo("기관");
        assertThat(request.title()).isEqualTo("공고");
        assertThat(request.source()).isEqualTo("ALIO");
        assertThat(request.sourceUrl()).isEqualTo("https://example.invalid/job/1");
        assertThat(request.externalId()).isEqualTo("123");
        assertThat(request.applicationStartAt()).isEqualTo(LocalDate.of(2026, 8, 1));
        assertThat(request.applicationEndAt()).isEqualTo(LocalDate.of(2026, 8, 31));
    }

    @Test
    void mapsMissingOptionalAndInvalidDateFieldsToNull() {
        AlioJobItem item = new AlioJobItem("기관", "공고", "not-a-url", null,
                "invalid", null, "", null, " ");

        JobPostingCreateRequest request = AlioJobMapper.from(item);

        assertThat(request.employmentType()).isNull();
        assertThat(request.jobCategory()).isNull();
        assertThat(request.location()).isNull();
        assertThat(request.sourceUrl()).isNull();
        assertThat(request.externalId()).isNull();
        assertThat(request.applicationStartAt()).isNull();
        assertThat(request.applicationEndAt()).isNull();
    }
}
