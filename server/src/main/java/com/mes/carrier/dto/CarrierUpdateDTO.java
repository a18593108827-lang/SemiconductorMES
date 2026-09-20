package com.mes.carrier.dto;

import lombok.Data;

@Data
public class CarrierUpdateDTO {

    private Integer capacity;
    private String cleanStatus;
    private String locationType;
    private String locationRef;
    private String remark;
}
