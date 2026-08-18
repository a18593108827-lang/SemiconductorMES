package com.mes.system.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 新增角色
 */
@Data
public class SysRoleCreateDTO {

    @NotBlank(message = "角色编码不能为空")
    private String roleCode;

    @NotBlank(message = "角色名称不能为空")
    private String roleName;

    private String remark;

    @NotNull(message = "状态不能为空")
    private Integer status;
}
