package com.mes.edc.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/** 特性启停 */
@Data
public class MesEdcParamEnabledDTO {

    @NotNull(message = "启停不能为空")
    @Min(value = 0, message = "只能为0或1")
    @Max(value = 1, message = "只能为0或1")
    private Integer enabled;
}
