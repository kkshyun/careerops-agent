package com.careerops.backend.collector.alio;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record AlioJobItem(
        String instNm,
        String recrutPbancTtl,
        String srcUrl,
        Long recrutPblntSn,
        String pbancBgngYmd,
        String pbancEndYmd,
        String recrutSeNm,
        String hireTypeNmLst,
        String acbgCondNmLst,
        String ongoingYn,
        String pblntInstCd,
        String ncsCdNmLst,
        String workRgnNmLst
) {
}
