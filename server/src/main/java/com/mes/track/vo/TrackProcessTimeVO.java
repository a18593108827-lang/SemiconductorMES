package com.mes.track.vo;

import lombok.Data;

import java.time.LocalDateTime;

/** 现场台展示的站内加工倒计时（只有 processing 且本站配了时长才有） */
@Data
public class TrackProcessTimeVO {
    /** 几点开的工（按下秒表） */
    private LocalDateTime startedAt;
    /** 最短加工分钟，null=不管下限 */
    private Integer minProcessMin;
    /** 最长加工分钟，null=不管上限 */
    private Integer maxProcessMin;
    /** 已经加工了多少分钟 */
    private Long elapsedMin;
    /** 还差多少分钟才到最短（到了就是 0） */
    private Long remainToMinMin;
    /** 离最长还剩多少分钟；负数=已经超时 */
    private Long remainToMaxMin;
    /** false=太短，完工按钮应禁用；超时仍是 true（允许出站） */
    private Boolean canTrackOutByTime;
    /** 已经超过最长加工时间 */
    private Boolean exceededMax;
    /** 现在点完工，出站后会自动锁批 */
    private Boolean willHoldOnOut;
}
