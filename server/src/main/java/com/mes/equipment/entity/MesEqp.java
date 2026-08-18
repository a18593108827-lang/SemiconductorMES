package com.mes.equipment.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import com.mes.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 设备主数据 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("mes_eqp")
public class MesEqp extends BaseEntity {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private String eqpCode;
    private String eqpName;
    private String eqpType;
    private String area;
    /** idle / running / down / pm / eng / offline */
    private String status;
    /** 1启用 0停用 */
    private Integer enabled;
    private String remark;

    /** 乐观锁版本号（并发更新防覆盖） */
    @Version
    private Integer version;

    private Long createBy;
    private Long updateBy;
}
