package com.mes.lot.dto;

import lombok.Data;

/** 批次分页查询 */
@Data
public class MesLotQuery {
    /** 批次号 / 产品编码 / 客户 Lot 关键字 */
    private String keyword;
    /** 状态：created / released / completed / scrapped */
    private String status;
    /** Hot 筛选：0 / 1 */
    private Integer hotFlag;
    private long page = 1;
    private long size = 20;
}
