package com.mes.equipment.dto;

import lombok.Data;

/** 设备分页查询 */
@Data
public class MesEqpQuery {
    /** 编码 / 名称 */
    private String keyword;
    /** idle / running / down / pm / eng / offline */
    private String status;
    private String eqpType;
    /** 1启用 0停用；空则全部 */
    private Integer enabled;
    private long page = 1;
    private long size = 20;
}
