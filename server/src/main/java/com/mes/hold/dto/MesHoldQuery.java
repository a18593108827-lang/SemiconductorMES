package com.mes.hold.dto;

import lombok.Data;

/** 锁批分页查询 */
@Data
public class MesHoldQuery {
    /** 批次号关键字 */
    private String keyword;
    /** active / released；空默认 active */
    private String status;
    /** 原因码精确匹配 */
    private String reasonCode;
    private long page = 1;
    private long size = 20;
}
