package com.mes.edc.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/** 改计划头（不含 items） */
@Data
public class MesEdcPlanUpdateDTO {

    @NotNull(message = "门禁开关不能为空")
    @Min(value = 0, message = "只能为0或1")
    @Max(value = 1, message = "只能为0或1")
    private Integer required;

    private String remark;
}
