package com.careerops.backend.collector.alio;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record AlioJobDetailItem(Long recrutPblntSn, List<AlioStepItem> steps, List<AlioFileItem> files) {
}
