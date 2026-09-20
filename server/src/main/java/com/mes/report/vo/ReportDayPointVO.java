package com.mes.report.vo;

import lombok.Data;

/** Move 报表：按日一点 */
@Data
public class ReportDayPointVO {

    /** yyyy-MM-dd */
    private String day;
    private long trackOutCount;
}
