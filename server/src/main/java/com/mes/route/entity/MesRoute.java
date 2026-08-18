package com.mes.route.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.mes.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 工艺路线壳（可执行内容在版本 mes_route_version）
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("mes_route")
public class MesRoute extends BaseEntity {

    /** 主键 */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 路线编码（唯一） */
    private String routeCode;

    /** 路线名称 */
    private String routeName;

    /** 关联产品编码（一期字符串，无 Product 表） */
    private String productCode;

    /** 状态：1正常 0停用（停用后不可新 Release） */
    private Integer status;

    /** 备注 */
    private String remark;
}
