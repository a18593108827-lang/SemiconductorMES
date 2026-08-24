package com.mes.history.dto;

import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDateTime;

/** 调查台分页：必须带 lotId 或 eqpId，禁止无过滤全表扫 */
@Data
public class HistoryQuery {
    private Long lotId;
    private Long eqpId;
    /** 精确匹配一个事务码；空则全部 */
    private String txType;
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    private LocalDateTime fromTime;
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    private LocalDateTime toTime;
    private long page = 1;
    private long size = 50;
}
