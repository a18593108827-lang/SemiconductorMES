package com.mes.carrier.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/** 绑定请求 */
@Data
public class CarrierBindDTO {

    @NotNull(message = "批次不能为空")
    private Long lotId;

    /** 载具 id 或 carrierCode */
    @NotBlank(message = "载具不能为空")
    private String carrierRef;
}
