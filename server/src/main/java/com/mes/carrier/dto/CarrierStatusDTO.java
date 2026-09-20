package com.mes.carrier.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/** 台账改态 */
@Data
public class CarrierStatusDTO {

    @NotBlank(message = "目标状态不能为空")
    private String status;

    private String remark;
}
