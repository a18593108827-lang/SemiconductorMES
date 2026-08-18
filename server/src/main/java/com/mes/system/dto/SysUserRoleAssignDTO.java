package com.mes.system.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 用户分配角色
 */
@Data
public class SysUserRoleAssignDTO {

    @NotNull(message = "roleIds不能为空")
    private List<Long> roleIds = new ArrayList<>();
}
