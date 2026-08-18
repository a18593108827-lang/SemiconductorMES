package com.mes.hold.dto;

import lombok.Data;

/** 预约锁批分页查询 */
@Data
public class MesFutureHoldQuery {
    /** 批次号关键字 */
    private String keyword;
    /** pending / activated / cancelled；空默认 pending；all=全部 */
    private String status;
    /** 原因码精确匹配 */
    private String reasonCode;
    private long page = 1;
    private long size = 20;
}
