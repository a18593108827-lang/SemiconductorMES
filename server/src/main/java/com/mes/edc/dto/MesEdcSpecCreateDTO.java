package com.mes.edc.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

/** 新建规格草稿 */
@Data
public class MesEdcSpecCreateDTO {

    @NotNull(message = "特性不能为空")
    private Long paramId;

    /** 空=全产品默认 */
    private String productCode;

    private BigDecimal usl;
    private BigDecimal lsl;
    private BigDecimal target;
    private String remark;
}
