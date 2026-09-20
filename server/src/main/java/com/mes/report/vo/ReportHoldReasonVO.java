package com.mes.report.vo;

import lombok.Data;

/** Hold 报表：按原因一行 */
@Data
public class ReportHoldReasonVO {

    private String reasonCode;
    private String reasonName;
    private long holdCount;
    private long activeCount;
    /** 无笔时为 null；有笔为平均分钟（向下取整后的算术平均再 ROUND） */
    private Long avgDurationMinutes;
}
