package com.mes.system.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 新增权限
 */
@Data
public class SysPermCreateDTO {

    /** 父ID，根为 0 */
    @NotNull(message = "父级不能为空")
    private Long parentId;

    /** 1目录 2菜单 3按钮 */
    @NotNull(message = "类型不能为空")
    private Integer permType;

    private String permCode;

    @NotBlank(message = "名称不能为空")
    private String permName;

    private String path;

    private String icon;

    @NotNull(message = "排序不能为空")
    private Integer sortNo;

    @NotNull(message = "状态不能为空")
    private Integer status;
}
