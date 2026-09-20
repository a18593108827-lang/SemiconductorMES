package com.mes.dashboard.facade;

import com.mes.dashboard.vo.DashboardOverviewVO;

/** 生产看板只读门面 */
public interface DashboardFacade {

    /**
     * 整屏聚合。
     * @param trendDays 1–31，默认 7，最大31
     * @param alarmLimit 报警流条数，默认 20，最大100
     */
    DashboardOverviewVO overview(int trendDays, int alarmLimit);
}
