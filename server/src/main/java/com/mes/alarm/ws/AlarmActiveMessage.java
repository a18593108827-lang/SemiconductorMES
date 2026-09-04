package com.mes.alarm.ws;

import lombok.Data;

/**
 * 推到 /topic/alarm.active 的摘要，给前端铃铛/列表刷新用。
 * 不是全量详情；要细看再调 GET /alarm/{id}。
 */
@Data
public class AlarmActiveMessage {

    /** 发生了啥：OPEN 新建 / BUMP 同键又响了一次 / ACK 有人确认 / CLEARED 已关闭 */
    private String action;
    /** 告警主键 */
    private Long id;
    /** 告警码，如 SPC_OOC */
    private String code;
    /** 级别：CRITICAL / WARNING / INFO */
    private String level;
    /** 当前状态：OPEN / ACK / CLEARED */
    private String status;
    /** 给人看的一句话 */
    private String message;
    /** 挂在哪类对象：LOT / EQP / CHART / NONE */
    private String entityType;
    /** 对象主键；没有对象时是 0 */
    private Long entityId;
    /** 同一条 OPEN 被 raise 了几次；BUMP 时会变大 */
    private Integer raiseCount;
}
