package com.mes.edc.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/** 新建量测特性 */
@Data
public class MesEdcParamCreateDTO {

    @NotBlank(message = "特性编码不能为空")
    private String paramCode;

    @NotBlank(message = "特性名称不能为空")
    private String paramName;

    /** 单位，可空 */
    private String unit;

    private String remark;
}
