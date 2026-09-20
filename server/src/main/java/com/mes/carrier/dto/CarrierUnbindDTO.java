package com.mes.carrier.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/** 按 Lot 解绑 */
@Data
public class CarrierUnbindDTO {

    @NotNull(message = "批次不能为空")
    private Long lotId;
}
