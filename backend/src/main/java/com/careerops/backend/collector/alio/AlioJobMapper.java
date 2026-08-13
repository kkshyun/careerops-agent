package com.careerops.backend.collector.alio;

import com.careerops.backend.job.dto.JobPostingCreateRequest;

import java.net.URI;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

public final class AlioJobMapper {

    private static final DateTimeFormatter ALIO_DATE = DateTimeFormatter.BASIC_ISO_DATE;

    private AlioJobMapper() {
    }

    public static JobPostingCreateRequest from(AlioJobItem item) {
        return new JobPostingCreateRequest(
                item.instNm(),
                item.recrutPbancTtl(),
                emptyToNull(item.recrutSeNm()),
                emptyToNull(item.ncsCdNmLst()),
                emptyToNull(item.workRgnNmLst()),
                parseDate(item.pbancBgngYmd()),
                parseDate(item.pbancEndYmd()),
                "ALIO",
                validHttpUrlOrNull(item.srcUrl()),
                item.recrutPblntSn() == null ? null : String.valueOf(item.recrutPblntSn())
        );
    }

    private static LocalDate parseDate(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(value, ALIO_DATE);
        } catch (DateTimeParseException exception) {
            return null;
        }
    }

    private static String validHttpUrlOrNull(String value) {
        String normalized = emptyToNull(value);
        if (normalized == null) {
            return null;
        }
        try {
            URI uri = URI.create(normalized);
            return (("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme()))
                    && uri.getHost() != null) ? normalized : null;
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private static String emptyToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
