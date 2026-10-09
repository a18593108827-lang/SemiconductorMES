package com.mes.test.dto;

import lombok.Data;

/** 登记时的一档 Bin */
@Data
public class TestBinLineDTO {

    /** HARD / SOFT */
    private String binType;

    /** Bin 号 */
    private String binCode;

    /** 颗数 */
    private Integer binQty;
}
