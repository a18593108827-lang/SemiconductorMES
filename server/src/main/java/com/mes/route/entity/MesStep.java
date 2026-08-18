package com.mes.route.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.mes.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 工序定义（可复用，挂到路线版本步骤上）
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("mes_step")
public class MesStep extends BaseEntity {

    /** 主键 */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 工序编码（唯一） */
    private String stepCode;

    /** 工序名称 */
    private String stepName;

    /** 类型：1加工 2量测 3其它 */
    private Integer stepType;

    /** 设备类型 */
    private String eqpType;

    /** 1允许Skip 0禁止 空=跟随全局（P1） */
    private Integer allowSkip;

    /** 站间最大等待分钟（P1） */
    private Integer maxQueueMin;

    /** 最短加工几分钟（进工艺路线快照）；空=不管下限 */
    private Integer minProcessMin;

    /** 最长加工几分钟（进工艺路线快照）；空=不管上限 */
    private Integer maxProcessMin;

    /** 状态：1正常 0禁用 */
    private Integer status;

    /** 备注 */
    private String remark;
}
