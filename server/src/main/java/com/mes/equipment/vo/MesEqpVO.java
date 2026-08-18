package com.mes.equipment.vo;

import lombok.Data;

import java.time.LocalDateTime;

/** 设备 */
@Data
public class MesEqpVO {
    private Long id;
    private String eqpCode;
    private String eqpName;
    private String eqpType;
    private String area;
    private String status;
    private Integer enabled;
    private String remark;
    private Integer version;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
