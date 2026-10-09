package com.mes.test.vo;

import lombok.Data;

import java.math.BigDecimal;

/** 一档 Bin 的颗数与占比 */
@Data
public class TestBinLineVO {

    /** HARD / SOFT */
    private String binType;

    /** Bin 号 */
    private String binCode;

    /** 登记时的名称 */
    private String binName;

    /** 颗数 */
    private Integer binQty;

    /** 登记时是否可出货：0 / 1 */
    private Integer isShippable;

    /** 颗数 / 本次总量，四位小数 */
    private BigDecimal ratio;
}
