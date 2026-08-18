package com.mes.hold.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/** 设置预约锁批入参 */
@Data
public class MesFutureHoldCreateDTO {

    @NotNull(message = "批次ID不能为空")
    private Long lotId;

    /** 目标站序（须存在于 Lot 放行快照，且为当前站或主路径之后） */
    @NotNull(message = "目标站序不能为空")
    private Integer targetSortNo;

    /** PRE / POST；空默认 PRE */
    private String timing;

    /** 原因码编码，须为启用中的 mes_hold_reason */
    @NotBlank(message = "原因码不能为空")
    private String reasonCode;

    /** 备注；原因码 OTHER 必填 */
    private String remark;
}
