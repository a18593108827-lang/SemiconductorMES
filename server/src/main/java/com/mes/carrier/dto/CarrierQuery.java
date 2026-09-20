package com.mes.carrier.dto;

import lombok.Data;

@Data
public class CarrierQuery {

    private String keyword;
    private String status;
    private String carrierType;
    private long page = 1;
    private long size = 20;
}
