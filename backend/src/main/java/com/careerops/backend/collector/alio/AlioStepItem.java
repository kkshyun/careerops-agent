package com.careerops.backend.collector.alio;

public record AlioStepItem(Integer sortNo, Long recrutStepSn, Long minStepSn, Long maxStepSn,
                           String recrutPbancTtl, Double cmpttRt, Integer aplyNope,
                           Integer recrutNope, String rsnOcrnYmd) {
}
