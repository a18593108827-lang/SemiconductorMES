package com.mes.equipment.vo;

import lombok.Data;

/** 选机下拉项 */
@Data
public class MesEqpOptionVO {
    private Long id;
    private String eqpCode;
    private String eqpName;
    private String eqpType;
    private String area;
    private String status;
}
