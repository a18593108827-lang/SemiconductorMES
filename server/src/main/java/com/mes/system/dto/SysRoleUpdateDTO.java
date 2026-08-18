package com.mes.system.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 编辑角色
 */
@Data
public class SysRoleUpdateDTO {

    @NotBlank(message = "角色名称不能为空")
    private String roleName;

    private String remark;

    @NotNull(message = "状态不能为空")
    private Integer status;
}
