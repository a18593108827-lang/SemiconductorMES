package com.mes.alarm.vo;

import lombok.Data;

/** 告警码表行 */
@Data
public class AlarmCodeVO {

    private String code;
    private String name;
    /** CRITICAL / WARNING / INFO */
    private String level;
    /** NONE / HOLD_LOT */
    private String onRaise;
    private String holdReasonCode;
    /** 1 启用 / 0 停用 */
    private Integer enabled;
    private String remark;
}
