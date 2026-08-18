package com.mes.system.dto;

import lombok.Data;

/**
 * 角色分页查询
 */
@Data
public class SysRoleQuery {

    private String keyword;
    private String roleCode;
    private String roleName;
    private Integer status;
    private long page = 1;
    private long size = 10;
}
