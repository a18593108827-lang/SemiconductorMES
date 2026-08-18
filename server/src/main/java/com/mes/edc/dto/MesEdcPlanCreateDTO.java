package com.mes.edc.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/** 新建站计划 */
@Data
public class MesEdcPlanCreateDTO {

    @NotNull(message = "工序不能为空")
    private Long stepId;

    /** 1=开 TrackOut 门禁；默认 0 */
    @Min(value = 0, message = "只能为0或1")
    @Max(value = 1, message = "只能为0或1")
    private Integer required;

    private String remark;
}
