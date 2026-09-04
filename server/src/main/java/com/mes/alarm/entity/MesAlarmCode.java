package com.mes.alarm.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.mes.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 告警码字典：每种告警叫什么、多严重、响了要不要顺带锁批。
 * 业务方 raise 只传 code，级别和策略从这里读。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("mes_alarm_code")
public class MesAlarmCode extends BaseEntity {

    /** 告警码本身，当主键，如 SPC_OOC */
    @TableId(type = IdType.INPUT)
    private String code;

    /** 界面上显示的名字 */
    private String name;
    /** 默认级别：CRITICAL 严重 / WARNING 警告 / INFO 提示；顶栏主要盯严重 */
    private String level;
    /** 一响之后干啥：NONE 只记告警；HOLD_LOT 顺带锁批（一期只用 NONE） */
    private String onRaise;
    /** 若选了锁批，用哪个 Hold 原因码 */
    private String holdReasonCode;
    /** 1=这码还能用；0=停用（raise 时当未知码处理，仍建议落一条） */
    private Integer enabled;
    /** 备注，给维护的人看 */
    private String remark;
}
