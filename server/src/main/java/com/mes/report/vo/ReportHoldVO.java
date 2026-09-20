package com.mes.report.vo;

import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Hold 原因分布：hold_time 落在窗内按原因汇总。
 */
@Data
public class ReportHoldVO {

    private LocalDateTime generatedAt;
    private boolean partial;
    private List<String> errors = new ArrayList<>();

    private LocalDate from;
    private LocalDate to;

    private List<ReportHoldReasonVO> byReason = new ArrayList<>();
    private long totalHold;
}
