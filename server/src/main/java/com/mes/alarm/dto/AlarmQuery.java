package com.mes.alarm.dto;

import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDateTime;

/** 告警分页查询 */
@Data
public class AlarmQuery {

    /** OPEN / ACK / CLEARED；空=不限 */
    private String status;
    /** CRITICAL / WARNING / INFO；空=不限 */
    private String level;
    /** 告警码精确匹配 */
    private String code;
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime from;
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime to;
    private long page = 1;
    private long size = 20;
}
