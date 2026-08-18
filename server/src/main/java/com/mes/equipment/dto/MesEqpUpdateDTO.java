package com.mes.equipment.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/** 编辑设备主数据（不含业务态） */
@Data
public class MesEqpUpdateDTO {

    @NotBlank(message = "设备名称不能为空")
    private String eqpName;

    private String eqpType;
    private String area;
    private String remark;
}
