package com.mes.alarm.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/** 更新告警码表；不改主键 code */
@Data
public class AlarmCodeUpdateDTO {

    @NotBlank(message = "名称不能为空")
    private String name;

    /** CRITICAL / WARNING / INFO */
    @NotBlank(message = "级别不能为空")
    private String level;

    /** NONE / HOLD_LOT */
    @NotBlank(message = "策略不能为空")
    private String onRaise;

    /** HOLD_LOT 时必填 */
    private String holdReasonCode;

    /** 1 启用 / 0 停用 */
    @NotNull(message = "启用状态不能为空")
    private Integer enabled;

    private String remark;
}
