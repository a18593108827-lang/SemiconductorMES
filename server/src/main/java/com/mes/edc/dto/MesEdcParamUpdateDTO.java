package com.mes.edc.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/** 改特性展示信息（编码不许改） */
@Data
public class MesEdcParamUpdateDTO {

    @NotBlank(message = "特性名称不能为空")
    private String paramName;

    private String unit;

    private String remark;
}
