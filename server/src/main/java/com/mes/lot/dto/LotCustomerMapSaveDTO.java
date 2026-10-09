package com.mes.lot.dto;

import lombok.Data;

/** 客户 Lot 映射登记 */
@Data
public class LotCustomerMapSaveDTO {

    /** INBOUND / OUTBOUND */
    private String mapType;

    /** 外部批号 */
    private String externalLotNo;

    /** 供应商或客户编码 */
    private String externalSource;

    private String customerCode;

    private Integer qty;

    private String remark;
}
