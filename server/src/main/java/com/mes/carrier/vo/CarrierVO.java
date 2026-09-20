package com.mes.carrier.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class CarrierVO {

    private Long id;
    private String carrierCode;
    private String carrierType;
    private Integer capacity;
    private String status;
    private String cleanStatus;
    private String locationType;
    private String locationRef;
    private String remark;
    private Integer version;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    /** 当前绑定 Lot；未绑为 null */
    private Long boundLotId;
    private String boundLotNo;
}
