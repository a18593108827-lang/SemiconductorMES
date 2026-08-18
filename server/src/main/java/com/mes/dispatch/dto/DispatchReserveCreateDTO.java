package com.mes.dispatch.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/** 发起设备预约 */
@Data
public class DispatchReserveCreateDTO {

    @NotNull(message = "批次ID不能为空")
    private Long lotId;

    @NotNull(message = "设备ID不能为空")
    private Long eqpId;

    private String remark;
}
