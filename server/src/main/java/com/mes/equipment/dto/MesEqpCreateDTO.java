package com.mes.equipment.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/** 新建设备 */
@Data
public class MesEqpCreateDTO {

    @NotBlank(message = "设备编码不能为空")
    private String eqpCode;

    @NotBlank(message = "设备名称不能为空")
    private String eqpName;

    private String eqpType;
    private String area;
    private String remark;
}
