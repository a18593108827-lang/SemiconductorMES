package com.mes.route.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/** 新建路线（自动生成 draft v1） */
@Data
public class MesRouteCreateDTO {

    @NotBlank(message = "路线编码不能为空")
    private String routeCode;

    @NotBlank(message = "路线名称不能为空")
    private String routeName;

    /** 产品编码（可选） */
    private String productCode;

    private String remark;
}
