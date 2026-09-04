package com.mes.alarm.vo;

import lombok.Data;

import java.time.LocalDateTime;

/** 告警列表 / 详情 */
@Data
public class AlarmVO {

    private Long id;
    /** 告警码 */
    private String code;
    /** 级别快照 */
    private String level;
    /** OPEN / ACK / CLEARED */
    private String status;
    private String message;
    /** LOT / EQP / CHART / NONE */
    private String entityType;
    private Long entityId;
    private String dedupeKey;
    /** 触发上下文 JSON 原文 */
    private String payloadJson;
    /** 同 OPEN 累加次数 */
    private Integer raiseCount;
    private LocalDateTime firstRaiseAt;
    private LocalDateTime lastRaiseAt;
    private Long ackBy;
    private LocalDateTime ackAt;
    private String ackRemark;
    private Long clearBy;
    private LocalDateTime clearAt;
    private String clearRemark;
}
