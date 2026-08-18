package com.mes.system.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.mes.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 权限/菜单
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_permission")
public class SysPermission extends BaseEntity {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long parentId;

    /** 1目录 2菜单 3按钮 */
    private Integer permType;

    private String permCode;

    private String permName;

    private String path;

    private String icon;

    private Integer sortNo;

    /** 1正常 0禁用 */
    private Integer status;
}
