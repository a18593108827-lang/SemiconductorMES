package com.mes.edc.vo;

import lombok.Data;

/** 计划项展示 */
@Data
public class MesEdcPlanItemVO {
    private Long id;
    private Long planId;
    private Long paramId;
    private String paramCode;
    private String paramName;
    private String unit;
    private Long specId;
    private Integer specVersionNo;
    private String specStatus;
    private Integer sortNo;
    private Integer mandatory;
}
