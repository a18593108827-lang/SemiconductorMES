package com.mes.system.dto;

import lombok.Data;

@Data
public class PermRequestQuery {

    /** pending / approved / rejected / cancelled */
    private String status;

    private long page = 1;

    private long size = 20;
}
