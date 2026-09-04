package com.mes.spc.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 最近一次失控摘要，给图上角标。 */
@Data
public class SpcEvalVO {
    private Long id;
    private Long chartId;
    private Long collectionItemId;
    private Integer ooc;
    private String ruleCode;
    private BigDecimal uclSnap;
    private BigDecimal clSnap;
    private BigDecimal lclSnap;
    private LocalDateTime createTime;
}
