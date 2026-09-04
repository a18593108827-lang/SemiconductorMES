package com.mes.spc.dto;

import lombok.Data;

import java.math.BigDecimal;

/** 手填三条控制限 */
@Data
public class SpcLimitsDTO {
    private BigDecimal ucl;
    private BigDecimal cl;
    private BigDecimal lcl;
}
