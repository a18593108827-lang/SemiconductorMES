package com.mes.edc.vo;

import lombok.Data;

import java.math.BigDecimal;

/** 采集点展示 */
@Data
public class MesEdcCollectionItemVO {
    private Long id;
    private Long collectionId;
    private Long paramId;
    private String paramCode;
    private String paramName;
    private String unit;
    private Long specId;
    private Integer specVersionNo;
    private BigDecimal uslSnap;
    private BigDecimal lslSnap;
    private BigDecimal valueNum;
    /** PASS / OOS */
    private String itemResult;
}
