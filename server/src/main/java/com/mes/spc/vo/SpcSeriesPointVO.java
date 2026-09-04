package com.mes.spc.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 趋势上的一个点。规格超了标 itemResult；控制限超了标 evalOoc。 */
@Data
public class SpcSeriesPointVO {
    private Long itemId;
    private Long collectionId;
    private Long lotId;
    private String lotNo;
    private LocalDateTime time;
    private BigDecimal value;
    private String itemResult;
    private Boolean evalOoc;
}
