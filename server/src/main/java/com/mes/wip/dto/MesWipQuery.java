package com.mes.wip.dto;

import lombok.Data;

/** 在制分页查询 */
@Data
public class MesWipQuery {
    /** 批次号 / 产品 / 客户 Lot */
    private String keyword;
    /** wait / processing / held；空则三者都查 */
    private String status;
    private String productCode;
    private Integer currentSortNo;
    private long page = 1;
    private long size = 20;
}
