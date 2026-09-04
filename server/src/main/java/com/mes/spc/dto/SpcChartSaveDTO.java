package com.mes.spc.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

/** 新建或改一张图。机台空着就是该站全部机。 */
@Data
public class SpcChartSaveDTO {

    private Long id;

    @NotNull(message = "特性不能为空")
    private Long paramId;

    @NotNull(message = "工序不能为空")
    private Long stepId;

    private Long eqpId;
    /** MANUAL / LEARNING */
    private String limitMode;
    private Integer learningN;
    private BigDecimal ucl;
    private BigDecimal cl;
    private BigDecimal lcl;
    private Integer runN;
    private Integer enabled;
}
