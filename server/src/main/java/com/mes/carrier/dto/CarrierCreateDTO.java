package com.mes.carrier.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class CarrierCreateDTO {

    @NotBlank(message = "载具编码不能为空")
    private String carrierCode;

    /** 默认 FOUP */
    private String carrierType;

    /** 默认 25 */
    private Integer capacity;

    private String cleanStatus;
    private String locationType;
    private String locationRef;
    private String remark;
}
