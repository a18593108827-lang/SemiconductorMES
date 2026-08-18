package com.mes.edc.dto;

import lombok.Data;

import java.math.BigDecimal;

/** 改规格草稿（仅 draft 可改） */
@Data
public class MesEdcSpecUpdateDTO {

    private BigDecimal usl;
    private BigDecimal lsl;
    private BigDecimal target;
    private String remark;
}
