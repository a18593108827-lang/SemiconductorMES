package com.mes.complaint.vo;

import lombok.Data;

/** preview 摘要计数（只读聚合） */
@Data
public class ComplaintPackageSummaryVO {

    /** 成员中存在 active Hold 的批次数 */
    private long activeHoldCount;
    /** 成员 Lot 上未关闭告警条数（OPEN+ACK） */
    private long openAlarmCount;
    /** 成员中 status=scrapped 的批次数 */
    private long scrapLotCount;
}
