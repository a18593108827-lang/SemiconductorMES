package com.mes.equipment.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/** 设备业务态改态 */
@Data
public class MesEqpStatusDTO {

    /** idle / running / down / pm / eng / offline */
    @NotBlank(message = "状态不能为空")
    private String status;
}
