package com.mes.dashboard.vo;

import lombok.Data;

/** 看板设备矩阵一行 */
@Data
public class DashboardEqpVO {

    private Long id;
    private String eqpCode;
    private String name;
    /** idle / running / down / pm / eng / offline */
    private String status;
    private String currentLotNo;
}
