package com.mes.spc.vo;

import lombok.Data;

import java.math.BigDecimal;

/** 一张 SPC 图的主数据。eqpId 空=该站全部机。 */
@Data
public class SpcChartVO {
    private Long id;
    private Long paramId;
    private Long stepId;
    private Long eqpId;
    private String chartType;
    private String limitMode;
    private Integer learningN;
    private BigDecimal ucl;
    private BigDecimal cl;
    private BigDecimal lcl;
    private Integer runN;
    private Integer enabled;
    /** 当前拿得到的点数 */
    private Integer n;
    private Integer version;
}
