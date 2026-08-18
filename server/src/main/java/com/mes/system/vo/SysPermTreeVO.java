package com.mes.system.vo;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 权限树节点
 */
@Data
public class SysPermTreeVO {

    private Long id;
    private Long parentId;
    private Integer permType;
    private String permCode;
    private String permName;
    private String path;
    private String icon;
    private Integer sortNo;
    private Integer status;
    private List<SysPermTreeVO> children = new ArrayList<>();
}
