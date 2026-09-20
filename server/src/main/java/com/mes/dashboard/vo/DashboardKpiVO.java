package com.mes.dashboard.vo;

import lombok.Data;

/**
 * 看板顶上一排数字卡片。
 * 都是「现在这样」的快照，不是报表里的历史汇总。
 */
@Data
public class DashboardKpiVO {

    /** 厂里还在跑/排队/被锁着的批次数（在制） */
    private long wipCount;

    /** 还没解锁的锁批条数（Hold 还挂着） */
    private long holdActiveCount;

    /**
     * 还没关掉的报警条数。
     * 刚响的（OPEN）和已经点过确认但还没关的（ACK）都算；关过的不算。
     */
    private long alarmOpenCount;

    /** 当前状态是故障（down）的设备台数 */
    private long eqpDownCount;

    /** 这次拉进来的启用设备一共多少台（矩阵里那批） */
    private long eqpTotal;

    /** 其中正在加工（running）的设备有多少台 */
    private long eqpRunningCount;
}
