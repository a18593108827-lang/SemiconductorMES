package com.mes.hold.vo;

import lombok.Data;

/** Hold 按原因聚合一行（报表用） */
@Data
public class HoldReasonAggVO {

    private String reasonCode;
    private long holdCount;
    private long activeCount;
    /** 窗内各笔时长（分钟，向下取整）的算术平均；无笔时不应出现 */
    private Long avgDurationMinutes;
}
