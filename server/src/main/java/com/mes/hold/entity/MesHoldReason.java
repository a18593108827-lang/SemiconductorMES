package com.mes.hold.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.mes.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 锁批原因码 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("mes_hold_reason")
public class MesHoldReason extends BaseEntity {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private String reasonCode;
    private String reasonName;
    /** quality / eng / customer / other */
    private String category;
    /** 1启用 0停用 */
    private Integer status;
    private String remark;
}
