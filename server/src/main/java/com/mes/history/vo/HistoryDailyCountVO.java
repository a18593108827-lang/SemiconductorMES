package com.mes.history.vo;

import lombok.Data;

/** 履历按日计数（看板 TrackOut 趋势） */
@Data
public class HistoryDailyCountVO {

    /** yyyy-MM-dd */
    private String day;
    private long count;
}
