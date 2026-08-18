package com.mes.system.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 角色分配权限
 */
@Data
public class SysRolePermAssignDTO {

    @NotNull(message = "permissionIds不能为空")
    private List<Long> permissionIds = new ArrayList<>();
}
