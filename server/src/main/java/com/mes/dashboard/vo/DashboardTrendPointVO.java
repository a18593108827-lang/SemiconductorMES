package com.mes.dashboard.vo;

import lombok.Data;

/** 看板产出趋势一日 */
@Data
public class DashboardTrendPointVO {

    /** yyyy-MM-dd */
    private String day;
    private long trackOutCount;
}
