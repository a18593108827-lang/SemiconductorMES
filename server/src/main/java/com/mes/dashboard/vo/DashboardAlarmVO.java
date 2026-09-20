package com.mes.dashboard.vo;

import lombok.Data;

import java.time.LocalDateTime;

/** 看板报警流一行 */
@Data
public class DashboardAlarmVO {

    private Long id;
    /** CRITICAL / WARNING / INFO */
    private String level;
    private String source;
    private String message;
    private LocalDateTime raisedAt;
    /** OPEN / ACK */
    private String status;
}
