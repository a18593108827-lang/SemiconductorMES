package com.mes.carrier.vo;

import lombok.Data;

import java.time.LocalDateTime;

/** Lot↔Carrier 当前绑定视图 */
@Data
public class CarrierBindingVO {

    private Long lotId;
    private String lotNo;
    private Long carrierId;
    private String carrierCode;
    private LocalDateTime bindTime;
    private Long bindBy;
}
