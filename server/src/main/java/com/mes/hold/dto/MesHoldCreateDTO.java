package com.mes.hold.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/** 发起锁批入参 */
@Data
public class MesHoldCreateDTO {

    @NotNull(message = "批次ID不能为空")
    private Long lotId;

    /** 原因码编码，如 Q_PENDING；须为启用中的 mes_hold_reason */
    @NotBlank(message = "原因码不能为空")
    private String reasonCode;

    /** 备注；原因码 OTHER 必填 */
    private String remark;
}
