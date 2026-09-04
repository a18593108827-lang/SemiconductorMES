package com.mes.spc.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/** 图启停：1开 0关 */
@Data
public class SpcEnabledDTO {

    @NotNull(message = "启停不能为空")
    @Min(value = 0, message = "只能为0或1")
    @Max(value = 1, message = "只能为0或1")
    private Integer enabled;
}
