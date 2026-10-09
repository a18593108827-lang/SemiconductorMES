package com.mes.lot.vo;

import lombok.Data;

import java.time.LocalDateTime;

/** 客户 Lot 映射 */
@Data
public class LotCustomerMapVO {

    private Long id;
    private Long lotId;
    private String lotNo;
    /** INBOUND / OUTBOUND */
    private String mapType;
    private String externalLotNo;
    private String externalSource;
    private String customerCode;
    private Integer qty;
    private String remark;
    private Long createBy;
    private LocalDateTime createTime;
}
