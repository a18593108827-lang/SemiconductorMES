package com.mes.report.vo;

import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Move（过站）汇总：按日 / 按站 TRACK_OUT。
 * 只读；partial 时 byDay 仍尽量可画（全 0）。
 */
@Data
public class ReportMoveVO {

    private LocalDateTime generatedAt;
    private boolean partial;
    private List<String> errors = new ArrayList<>();

    /** 回显查询窗（含两端） */
    private LocalDate from;
    private LocalDate to;

    private List<ReportDayPointVO> byDay = new ArrayList<>();
    /** Rep-1 固定空列表；Rep-2 填按站 */
    private List<ReportStepPointVO> byStep = new ArrayList<>();

    private long totalTrackOut;
}
