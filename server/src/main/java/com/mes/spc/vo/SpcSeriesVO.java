package com.mes.spc.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/** 一张图的趋势。规格限只展示，不参与失控判定。 */
@Data
public class SpcSeriesVO {
    private SpcChartVO chart;
    private BigDecimal specUsl;
    private BigDecimal specLsl;
    private BigDecimal cpk;
    private BigDecimal cp;
    private List<SpcSeriesPointVO> points = new ArrayList<>();
    private SpcEvalVO lastEval;
}
