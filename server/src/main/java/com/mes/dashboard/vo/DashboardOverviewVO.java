package com.mes.dashboard.vo;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 看板整屏一次拉回来的数据包。
 * 前端只调这一个接口就能画：顶上数字、设备格子、报警列表、下面折线。
 */
@Data
public class DashboardOverviewVO {

    /** 这次数据是什么时候算出来的（用来显示「刚才更新」） */
    private LocalDateTime generatedAt;

    /**
     * 是不是有一块没查成功。
     * true=页面还能用，但某一块可能空或过时；false=各块都正常。
     */
    private boolean partial;

    /** partial 为 true 时，说明哪一块挂了、大概啥原因（给人看的短句） */
    private List<String> errors = new ArrayList<>();

    /** 顶上一排数字：在制、锁批、报警、故障台数等 */
    private DashboardKpiVO kpi = new DashboardKpiVO();

    /** 中间设备状态格子；每台机一行，带当前在跑的批号（有的话） */
    private List<DashboardEqpVO> equipment = new ArrayList<>();

    /** 右边/下面的报警流水；还没关掉的那些，新的在前 */
    private List<DashboardAlarmVO> alarms = new ArrayList<>();

    /** 近几天每天出了多少次站（TrackOut），画产出趋势折线用；缺的天也是 0 */
    private List<DashboardTrendPointVO> outputTrend = new ArrayList<>();
}
