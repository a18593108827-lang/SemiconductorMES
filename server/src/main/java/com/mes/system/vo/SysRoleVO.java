package com.mes.system.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 角色列表项
 */
@Data
public class SysRoleVO {

    private Long id;
    private String roleCode;
    private String roleName;
    private String remark;
    private Integer status;
    private Long userCount;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
